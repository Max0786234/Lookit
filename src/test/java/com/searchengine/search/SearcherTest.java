package com.searchengine.search;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;
import com.searchengine.index.Fields;
import com.searchengine.index.MemoryIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearcherTest {

    private final Analyzer analyzer = Analyzer.english();
    private MemoryIndex index;
    private Searcher searcher;

    @BeforeEach
    void setUp() {
        index = new MemoryIndex(analyzer);
        index.add(new Document(1, "Java Search Engine", "u1",
                "Java is a powerful language. This project builds a search engine using Java."));
        index.add(new Document(2, "Spring Boot Backend", "u2",
                "Spring Boot helps Java developers build backend APIs quickly."));
        index.add(new Document(3, "Information Retrieval Basics", "u3",
                "Search engines use an inverted index and ranking algorithms to retrieve documents."));
        searcher = new Searcher(index, new Bm25Similarity(), Map.of(Fields.TITLE, 1.0, Fields.BODY, 1.0));
    }

    private List<Integer> ids(Query q) {
        return searcher.search(q, 10).hits().stream().map(TopDocs.ScoredDoc::docId).toList();
    }

    @Test
    void bagOfWordsRanksDocWithMostEvidenceFirst() {
        TopDocs top = searcher.search(QueryBuilders.bagOfWords(analyzer, "java search"), 10);
        assertEquals(3, top.totalHits());
        assertEquals(1, top.hits().get(0).docId());
        assertTrue(top.hits().get(0).score() > top.hits().get(1).score());
    }

    @Test
    void stemmingMatchesMorphologicalVariants() {
        assertEquals(List.of(3), ids(QueryBuilders.bagOfWords(analyzer, "retrieving")));
    }

    @Test
    void phraseRequiresAdjacencyAndOrder() {
        assertEquals(List.of(1, 3), ids(QueryBuilders.phrase(analyzer, null, "search engine")).stream().sorted().toList());
        assertTrue(ids(QueryBuilders.phrase(analyzer, null, "engine search")).isEmpty());
    }

    @Test
    void phraseDoesNotCrossFieldBoundary() {
        // title ends with "Engine" and body starts with "Java": "engine java" must not match
        assertTrue(ids(QueryBuilders.phrase(analyzer, null, "engine java")).isEmpty());
    }

    @Test
    void phraseHonoursStopwordGaps() {
        index.add(new Document(4, "t", "u4", "the bank of america"));
        index.add(new Document(5, "t", "u5", "america bank"));
        index.add(new Document(6, "t", "u6", "bank america"));
        assertEquals(List.of(4), ids(QueryBuilders.phrase(analyzer, null, "bank of america")));
    }

    @Test
    void booleanMustAndMustNot() {
        Query q = new Query.Bool(List.of(
                new Query.Clause(Query.Occur.MUST, new Query.Term(null, "java")),
                new Query.Clause(Query.Occur.MUST_NOT, new Query.Term(null, "spring"))));
        assertEquals(List.of(1), ids(q));

        Query both = new Query.Bool(List.of(
                new Query.Clause(Query.Occur.MUST, new Query.Term(null, "java")),
                new Query.Clause(Query.Occur.MUST, new Query.Term(null, "backend"))));
        assertEquals(List.of(2), ids(both));
    }

    @Test
    void mustWithOptionalShouldOnlyBoosts() {
        Query q = new Query.Bool(List.of(
                new Query.Clause(Query.Occur.MUST, new Query.Term(null, "java")),
                new Query.Clause(Query.Occur.SHOULD, new Query.Term(null, "backend"))));
        assertEquals(List.of(2, 1), ids(q));
    }

    @Test
    void fieldRestrictedTermAndPrefix() {
        assertEquals(List.of(1), ids(new Query.Term(Fields.TITLE, "java")));
        assertEquals(List.of(2), ids(new Query.Prefix(Fields.BODY, "develop")));
        assertEquals(List.of(1, 3), ids(new Query.Prefix(null, "engin")).stream().sorted().toList());
    }

    @Test
    void topKKeepsBestAndReportsTotal() {
        for (int i = 10; i < 60; i++) {
            index.add(new Document(i, "t", "u", "filler " + "word ".repeat(i % 7) + "zebra"));
        }
        TopDocs top = searcher.search(QueryBuilders.bagOfWords(analyzer, "zebra"), 5);
        assertEquals(50, top.totalHits());
        assertEquals(5, top.hits().size());
        for (int i = 1; i < 5; i++) {
            assertTrue(top.hits().get(i - 1).score() >= top.hits().get(i).score());
        }
    }

    @Test
    void unknownTermsAndEmptyQueries() {
        assertTrue(ids(QueryBuilders.bagOfWords(analyzer, "quantum")).isEmpty());
        assertTrue(ids(QueryBuilders.bagOfWords(analyzer, "the of")).isEmpty());
    }
}
