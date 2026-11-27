package com.searchengine.search;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;
import com.searchengine.index.Fields;
import com.searchengine.index.MemoryIndex;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bm25fSearcherTest {

    private final Analyzer analyzer = Analyzer.english();

    private static Map<String, Bm25fSearcher.FieldParams> params(double titleWeight) {
        return Map.of(Fields.TITLE, new Bm25fSearcher.FieldParams(titleWeight, 0.75),
                Fields.BODY, new Bm25fSearcher.FieldParams(1.0, 0.75));
    }

    @Test
    void scoreMatchesTheBm25fFormula() {
        MemoryIndex index = new MemoryIndex(analyzer);
        index.add(new Document(1, "zebra", "u", "zebra zebra horse"));
        index.add(new Document(2, "horse", "u", "horse cow"));
        Bm25fSearcher s = new Bm25fSearcher(index, 1.2, params(2.0));

        double score = s.search(new Query.Term(null, "zebra"), 10).hits().get(0).score();

        double avgTitle = 1.0;
        double avgBody = 2.5;
        double tf = 2.0 * 1 / (1 - 0.75 + 0.75 * 1 / avgTitle) + 1.0 * 2 / (1 - 0.75 + 0.75 * 3 / avgBody);
        double expected = Bm25Similarity.idf(2, 1) * tf * 2.2 / (1.2 + tf);
        assertEquals(expected, score, 1e-9);
    }

    @Test
    void titleWeightPrefersTitleMatches() {
        MemoryIndex index = new MemoryIndex(analyzer);
        index.add(new Document(1, "alpha", "u", "kiwi beta gamma"));
        index.add(new Document(2, "kiwi", "u", "alpha beta gamma"));
        Bm25fSearcher s = new Bm25fSearcher(index, 1.2, params(3.0));

        List<Integer> ranked = s.search(new Query.Term(null, "kiwi"), 10).hits().stream()
                .map(TopDocs.ScoredDoc::docId).toList();
        assertEquals(List.of(2, 1), ranked);
    }

    @Test
    void termInBothFieldsSaturatesOnceNotTwice() {
        MemoryIndex index = new MemoryIndex(analyzer);
        index.add(new Document(1, "kiwi", "u", "kiwi"));
        index.add(new Document(2, "x", "u", "y"));
        Bm25fSearcher bm25f = new Bm25fSearcher(index, 1.2, params(1.0));
        Searcher perField = new Searcher(index, new Bm25Similarity(1.2, 0.75), Map.of(Fields.TITLE, 1.0, Fields.BODY, 1.0));

        double combined = bm25f.search(new Query.Term(null, "kiwi"), 1).hits().get(0).score();
        double summed = perField.search(new Query.Term(null, "kiwi"), 1).hits().get(0).score();
        double cap = Bm25Similarity.idf(2, 1) * 2.2;
        assertTrue(combined < cap, "BM25F is bounded by one saturation curve");
        assertTrue(summed > combined, "summing per-field BM25 double-counts the term");
    }

    @Test
    void phrasesAndBooleanQueriesWork() {
        MemoryIndex index = new MemoryIndex(analyzer);
        index.add(new Document(1, "search engine", "u", "an inverted index"));
        index.add(new Document(2, "engine search", "u", "search engine internals"));
        index.add(new Document(3, "other", "u", "engine of search"));
        Bm25fSearcher s = new Bm25fSearcher(index, 1.2, params(2.0));

        List<Integer> phrase = s.search(QueryBuilders.phrase(analyzer, null, "search engine"), 10).hits().stream()
                .map(TopDocs.ScoredDoc::docId).sorted().toList();
        assertEquals(List.of(1, 2), phrase);

        Query mustNot = new Query.Bool(List.of(
                new Query.Clause(Query.Occur.MUST, new Query.Term(null, "engin")),
                new Query.Clause(Query.Occur.MUST_NOT, new Query.Term(null, "invert"))));
        assertEquals(List.of(2, 3), s.search(mustNot, 10).hits().stream().map(TopDocs.ScoredDoc::docId).sorted().toList());
    }
}
