package com.searchengine.search;

import com.searchengine.analysis.Analyzer;
import com.searchengine.analysis.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses user query syntax into a {@link Query}. Grammar (lowest precedence first):
 * <pre>
 *   query   := orExpr
 *   orExpr  := andExpr ( "OR" andExpr )*
 *   andExpr := seq ( "AND" seq )*
 *   seq     := unary+                      juxtaposition: SHOULD, or MUST with "+", MUST_NOT with "-"/"NOT"
 *   unary   := ( "+" | "-" | "NOT" )? primary
 *   primary := "(" query ")" | field ":" primary | '"' words '"' | word "*" | word
 * </pre>
 * Operators are case-sensitive (upper-case only) so natural-language queries containing
 * "or"/"and"/"not" behave as plain words. The parser never throws: unbalanced quotes and
 * parentheses are closed implicitly, unknown fields are treated as text.
 */
public final class QueryParser {
    private final Analyzer analyzer;
    private final Set<String> fields;

    private List<String> tokens;
    private int pos;

    public QueryParser(Analyzer analyzer, Set<String> fields) {
        this.analyzer = analyzer;
        this.fields = Set.copyOf(fields);
    }

    public synchronized Query parse(String text) {
        tokens = lex(text == null ? "" : text);
        pos = 0;
        Query q = orExpr(null);
        return simplify(q);
    }

    // ---- lexer: words, quoted strings (kept with their quotes), and the symbols ( ) + - : *

    static List<String> lex(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"') {
                int end = s.indexOf('"', i + 1);
                if (end < 0) {
                    end = s.length();
                }
                out.add("\"" + s.substring(i + 1, end));
                i = end + 1;
            } else if (c == '(' || c == ')') {
                out.add(String.valueOf(c));
                i++;
            } else if ((c == '+' || c == '-') && (out.isEmpty() || startsTerm(s, i))) {
                // only a prefix operator at the start of a word, so "covid-19" stays one word
                out.add(String.valueOf(c));
                i++;
            } else {
                int start = i;
                while (i < s.length() && !Character.isWhitespace(s.charAt(i))
                        && "()\"".indexOf(s.charAt(i)) < 0) {
                    i++;
                }
                String word = s.substring(start, i);
                if (word.codePoints().noneMatch(Character::isLetterOrDigit)) {
                    continue; // stray punctuation such as "(+)-" must not look like an operator
                }
                int colon = word.indexOf(':');
                if (colon > 0 && colon < word.length() - 1 || colon > 0 && i < s.length() && "(\"".indexOf(s.charAt(i)) >= 0) {
                    out.add(word.substring(0, colon));
                    out.add(":");
                    if (colon < word.length() - 1) {
                        out.add(word.substring(colon + 1));
                    }
                } else {
                    out.add(word);
                }
            }
        }
        return out;
    }

    private static boolean startsTerm(String s, int i) {
        boolean atWordStart = i == 0 || Character.isWhitespace(s.charAt(i - 1)) || s.charAt(i - 1) == '(';
        return atWordStart && i + 1 < s.length() && !Character.isWhitespace(s.charAt(i + 1));
    }

    // ---- recursive descent

    private Query orExpr(String field) {
        List<Query> alternatives = new ArrayList<>();
        alternatives.add(andExpr(field));
        while (peek("OR")) {
            pos++;
            alternatives.add(andExpr(field));
        }
        if (alternatives.size() == 1) {
            return alternatives.get(0);
        }
        return new Query.Bool(alternatives.stream()
                .map(q -> new Query.Clause(Query.Occur.SHOULD, q)).toList());
    }

    private Query andExpr(String field) {
        List<Query> required = new ArrayList<>();
        required.add(seq(field));
        while (peek("AND")) {
            pos++;
            required.add(seq(field));
        }
        if (required.size() == 1) {
            return required.get(0);
        }
        return new Query.Bool(required.stream()
                .map(q -> new Query.Clause(Query.Occur.MUST, q)).toList());
    }

    private Query seq(String field) {
        List<Query.Clause> clauses = new ArrayList<>();
        while (pos < tokens.size() && !peek(")") && !peek("OR") && !peek("AND")) {
            Query.Occur occur = Query.Occur.SHOULD;
            if (peek("+")) {
                occur = Query.Occur.MUST;
                pos++;
            } else if (peek("-") || peek("NOT")) {
                occur = Query.Occur.MUST_NOT;
                pos++;
            }
            if (pos >= tokens.size() || peek(")")) {
                break;
            }
            Query q = primary(field);
            boolean empty = q == null || q instanceof Query.Bool b && b.clauses().isEmpty();
            Query.Clause clause = empty ? null : new Query.Clause(occur, q);
            if (clause == null) {
                continue;
            }
            // An all-SHOULD group under SHOULD scores identically flattened ("covid-19" -> covid, 19).
            List<Query.Clause> parts = occur == Query.Occur.SHOULD && q instanceof Query.Bool b
                    && b.clauses().stream().allMatch(c -> c.occur() == Query.Occur.SHOULD) ? b.clauses() : List.of(clause);
            for (Query.Clause part : parts) {
                // a repeated word does not multiply its weight (BM25F parameters were tuned this way)
                if (!clauses.contains(part)) {
                    clauses.add(part);
                }
            }
        }
        if (clauses.size() == 1 && clauses.get(0).occur() == Query.Occur.SHOULD) {
            return clauses.get(0).query();
        }
        return new Query.Bool(clauses);
    }

    private Query primary(String field) {
        String tok = tokens.get(pos++);
        if (tok.equals("(")) {
            Query inner = orExpr(field);
            if (peek(")")) {
                pos++;
            }
            return inner;
        }
        if (peek(":") && fields.contains(tok.toLowerCase(Locale.ROOT)) && pos + 1 < tokens.size()) {
            pos++;
            return primary(tok.toLowerCase(Locale.ROOT));
        }
        if (peek(":")) {
            // unknown field: read "foo:bar" as the two words
            pos++;
            return wordQuery(tok, field);
        }
        if (tok.startsWith("\"")) {
            Query phrase = QueryBuilders.phrase(analyzer, field, tok.substring(1));
            return phrase instanceof Query.Phrase ? phrase : null;
        }
        if (tok.equals("+") || tok.equals("-") || tok.equals(")")) {
            return null;
        }
        if (tok.endsWith("*") && tok.length() > 1) {
            List<String> surface = Analyzer.simple().terms(tok.substring(0, tok.length() - 1));
            if (surface.size() == 1) {
                return new Query.Prefix(field, surface.get(0));
            }
        }
        return wordQuery(tok, field);
    }

    /**
     * A single word. If the analyzer splits it ("covid-19") the parts become optional terms, like
     * Lucene with autoGeneratePhraseQueries=false: strict phrases here cost 1.8 points of Recall@100
     * on SciFact, where hyphenated biomedical names are written inconsistently.
     */
    private Query wordQuery(String word, String field) {
        List<Token> analyzed = analyzer.analyze(word);
        if (analyzed.isEmpty()) {
            return null;
        }
        if (analyzed.size() == 1) {
            return new Query.Term(field, analyzed.get(0).term());
        }
        return new Query.Bool(analyzed.stream()
                .map(t -> new Query.Clause(Query.Occur.SHOULD, new Query.Term(field, t.term())))
                .toList());
    }

    private boolean peek(String s) {
        return pos < tokens.size() && tokens.get(pos).equals(s);
    }

    /** Flatten single-clause SHOULD booleans; dedupe nothing else so scoring stays explicit. */
    private static Query simplify(Query q) {
        if (q instanceof Query.Bool b) {
            List<Query.Clause> clauses = b.clauses().stream()
                    .map(c -> new Query.Clause(c.occur(), simplify(c.query())))
                    .toList();
            if (clauses.size() == 1 && clauses.get(0).occur() != Query.Occur.MUST_NOT) {
                return clauses.get(0).query();
            }
            return new Query.Bool(clauses);
        }
        return q;
    }
}
