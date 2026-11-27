package com.searchengine.search;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;
import com.searchengine.index.MemoryIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuerySuggesterTest {

    private final Analyzer analyzer = Analyzer.english();
    private final MemoryIndex index = new MemoryIndex(analyzer);
    private final QuerySuggester suggester = new QuerySuggester();

    @BeforeEach
    void setUp() {
        String[] docs = {
                "The inverted index makes retrieval fast. Retrieval quality depends on ranking.",
                "Ranking algorithms such as BM25 rank documents by relevance.",
                "Search engines crawl the web, index pages and rank them.",
                "Retrieval augmented generation retrieves passages for a language model.",
                "Rabbits are not related to ranking."};
        for (int i = 0; i < docs.length; i++) {
            index.add(new Document(i, "doc", "u", docs[i]));
            suggester.add(docs[i]);
        }
    }

    @Test
    void correctsUnknownWordsByEditDistanceThenFrequency() {
        assertEquals("inverted index", suggester.didYouMean("invertd index", index, analyzer));
        assertEquals("retrieval ranking", suggester.didYouMean("retreival rankng", index, analyzer), "transposition");
        assertEquals("Search +ranking", suggester.didYouMean("Search +rankign", index, analyzer), "syntax is preserved");
    }

    @Test
    void knownWordsStemmedVariantsAndNumbersAreLeftAlone() {
        assertNull(suggester.didYouMean("search engines", index, analyzer), "'engines' matches via its stem");
        assertNull(suggester.didYouMean("bm25 retrieving", index, analyzer));
        assertNull(suggester.didYouMean("zzzzzz", index, analyzer), "nothing close enough");
        assertEquals("ranking AND ranking", suggester.didYouMean("ranking AND rankng", index, analyzer), "operators untouched");
    }

    @Test
    void autocompletesTheLastWordByFrequency() {
        assertEquals(List.of("retrieval", "retrieves"), suggester.complete("retr", 5));
        assertEquals(List.of("fast ranking", "fast rank", "fast rabbits"), suggester.complete("fast ra", 3));
        assertEquals(List.of(), suggester.complete("ranking ", 5), "trailing space: word finished");
        assertEquals(List.of(), suggester.complete("", 5));
    }

    @Test
    void damerauLevenshteinDistance() {
        assertEquals(0, QuerySuggester.damerauLevenshtein("rank", "rank", 2));
        assertEquals(1, QuerySuggester.damerauLevenshtein("rnak", "rank", 2));
        assertEquals(2, QuerySuggester.damerauLevenshtein("kitten", "sittin", 2));
        assertEquals(3, QuerySuggester.damerauLevenshtein("kitten", "sitting", 2), "capped at max + 1");
    }
}
