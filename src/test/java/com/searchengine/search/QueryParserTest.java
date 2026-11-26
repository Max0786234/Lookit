package com.searchengine.search;

import com.searchengine.analysis.Analyzer;
import com.searchengine.index.Fields;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryParserTest {

    private final QueryParser parser = new QueryParser(Analyzer.english(), Set.of(Fields.TITLE, Fields.BODY));

    private static Query.Term term(String t) {
        return new Query.Term(null, t);
    }

    private static Query.Clause should(Query q) {
        return new Query.Clause(Query.Occur.SHOULD, q);
    }

    private static Query.Clause must(Query q) {
        return new Query.Clause(Query.Occur.MUST, q);
    }

    private static Query.Clause mustNot(Query q) {
        return new Query.Clause(Query.Occur.MUST_NOT, q);
    }

    @Test
    void plainWordsAreAnalyzedShouldClauses() {
        assertEquals(new Query.Bool(List.of(should(term("search")), should(term("engin")))),
                parser.parse("Search the Engines"));
        assertEquals(term("java"), parser.parse("java"));
    }

    @Test
    void plusMinusAndNot() {
        assertEquals(new Query.Bool(List.of(must(term("java")), mustNot(term("spring")), should(term("index")))),
                parser.parse("+java -spring index"));
        assertEquals(new Query.Bool(List.of(should(term("java")), mustNot(term("spring")))),
                parser.parse("java NOT spring"));
    }

    @Test
    void andBindsTighterThanOr() {
        Query q = parser.parse("java AND spring OR lucene");
        assertEquals(new Query.Bool(List.of(
                should(new Query.Bool(List.of(must(term("java")), must(term("spring"))))),
                should(term("lucen")))), q);
    }

    @Test
    void lowercaseOperatorsAreJustWords() {
        // lower-case "or" is an ordinary word (here also a stopword), not the OR operator
        assertEquals(new Query.Bool(List.of(should(term("cat")), should(term("dog")))), parser.parse("cat or dog"));
    }

    @Test
    void phrasesFieldsPrefixesAndGroups() {
        assertEquals(new Query.Phrase(null, List.of("search", "engin"), List.of(0, 1)), parser.parse("\"search engines\""));
        assertEquals(new Query.Term(Fields.TITLE, "java"), parser.parse("title:Java"));
        assertEquals(new Query.Phrase(Fields.BODY, List.of("invert", "index"), List.of(0, 1)),
                parser.parse("body:\"inverted index\""));
        assertEquals(new Query.Prefix(null, "retriev"), parser.parse("retriev*"));
        assertEquals(new Query.Bool(List.of(must(term("java")),
                        must(new Query.Bool(List.of(should(term("spring")), should(term("lucen"))))))),
                parser.parse("+java +(spring OR lucene)"));
        assertEquals(new Query.Bool(List.of(should(term("java")), should(term("spring")), should(term("lucen")))),
                parser.parse("java (spring lucene)"));
    }

    @Test
    void hyphenatedWordsSplitIntoOptionalTermsNotNegations() {
        assertEquals(new Query.Bool(List.of(should(term("covid")), should(term("19")))), parser.parse("covid-19"));
        assertEquals(new Query.Bool(List.of(should(term("amp")), should(term("activ")), should(term("kinas")))),
                parser.parse("AMP-activated kinase activation"));
        assertEquals(new Query.Bool(List.of(should(term("β")), should(term("cell")))), parser.parse("β-cell"));
    }

    @Test
    void strayPunctuationIsNotAnOperatorAndRepeatsAreDeduplicated() {
        assertEquals(new Query.Bool(List.of(should(term("alpha")), should(term("thalassemia")))),
                parser.parse("alpha (+)- thalassemia"));
        assertEquals(new Query.Bool(List.of(should(term("variant")), should(term("rare")))),
                parser.parse("variants rare variant"));
    }

    @Test
    void unknownFieldIsTreatedAsText() {
        assertEquals(new Query.Bool(List.of(should(term("ratio")), should(term("5")))), parser.parse("ratio:5"));
    }

    @Test
    void malformedInputNeverThrows() {
        for (String s : new String[]{"\"unterminated phrase", "((java", "java)", "-", "+", "title:", "AND OR",
                ":", "\"\"", "*", "NOT", ""}) {
            Query q = parser.parse(s);
            assertTrue(q != null, s);
        }
        assertInstanceOf(Query.Phrase.class, parser.parse("\"open phrase"));
    }
}
