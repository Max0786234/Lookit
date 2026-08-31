package com.searchengine.query;

import com.searchengine.document.Document;
import com.searchengine.index.InvertedIndex;
import com.searchengine.processor.TextProcessor;
import com.searchengine.ranking.Bm25Ranker;
import com.searchengine.ranking.TermFrequencyRanker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchServiceTest {

    @Test
    void searchUsesTermFrequencyRankerForSimpleQueries() {
        SearchService searchService = new SearchService(sampleIndex(), new TextProcessor(), new TermFrequencyRanker());

        List<SearchResult> results = searchService.search("java search");

        assertEquals(3, results.size());
        assertEquals(1, results.get(0).document().id());
        assertTrue(results.get(0).score() > results.get(1).score());
    }

    @Test
    void searchPhraseReturnsOnlyDocumentsWithConsecutiveTerms() {
        SearchService searchService = new SearchService(sampleIndex(), new TextProcessor(), new Bm25Ranker());

        List<SearchResult> matchingResults = searchService.search("\"search engine\"");
        List<SearchResult> reversedResults = searchService.search("\"engine search\"");

        assertEquals(1, matchingResults.size());
        assertEquals(1, matchingResults.get(0).document().id());
        assertTrue(reversedResults.isEmpty());
    }

    @Test
    void searchWithNoMatchesReturnsEmptyList() {
        SearchService searchService = new SearchService(sampleIndex(), new TextProcessor(), new Bm25Ranker());

        List<SearchResult> results = searchService.search("quantum computing");

        assertTrue(results.isEmpty());
    }

    private InvertedIndex sampleIndex() {
        TextProcessor textProcessor = new TextProcessor();
        InvertedIndex index = new InvertedIndex(textProcessor);

        index.addDocument(new Document(
                1,
                "Java Search Engine",
                "local://java-search-engine",
                "Java is a powerful language. This project builds a search engine using Java."
        ));

        index.addDocument(new Document(
                2,
                "Spring Boot Backend",
                "local://spring-boot-backend",
                "Spring Boot helps Java developers build backend APIs quickly."
        ));

        index.addDocument(new Document(
                3,
                "Information Retrieval Basics",
                "local://information-retrieval",
                "Search engines use an inverted index and ranking algorithms to retrieve documents."
        ));

        return index;
    }
}
