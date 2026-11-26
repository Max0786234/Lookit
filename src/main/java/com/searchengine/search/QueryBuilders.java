package com.searchengine.search;

import com.searchengine.analysis.Analyzer;
import com.searchengine.analysis.Token;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds queries from free text without the full query language. */
public final class QueryBuilders {
    private QueryBuilders() {
    }

    /** OR of the distinct analyzed terms, searched in all default fields. */
    public static Query bagOfWords(Analyzer analyzer, String text) {
        Set<String> terms = new LinkedHashSet<>(analyzer.terms(text));
        List<Query.Clause> clauses = new ArrayList<>();
        for (String t : terms) {
            clauses.add(new Query.Clause(Query.Occur.SHOULD, new Query.Term(null, t)));
        }
        return new Query.Bool(clauses);
    }

    /** Exact phrase; stopword gaps are preserved through analyzer positions. */
    public static Query phrase(Analyzer analyzer, String field, String text) {
        List<Token> tokens = analyzer.analyze(text);
        if (tokens.isEmpty()) {
            return new Query.Bool(List.of());
        }
        return new Query.Phrase(field,
                tokens.stream().map(Token::term).toList(),
                tokens.stream().map(Token::position).toList());
    }
}
