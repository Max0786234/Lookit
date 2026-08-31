package com.searchengine;

import com.searchengine.document.Document;
import com.searchengine.index.InvertedIndex;
import com.searchengine.index.Posting;
import com.searchengine.processor.TextProcessor;
import com.searchengine.query.SearchResult;
import com.searchengine.query.SearchService;
import com.searchengine.ranking.Bm25Ranker;
import com.searchengine.ranking.TermFrequencyRanker;
import com.searchengine.ranking.TfIdfRanker;

import java.util.List;

public class SearchEngineTests {
    public static void main(String[] args) {
        testTokenization();
        testInvertedIndexStoresFrequencyAndPositions();
        testTermFrequencySearchRanking();
        testPhraseSearch();
        testTfIdfRewardsRarerTerms();
        testBm25Ranking();

        System.out.println("All tests passed.");
    }

    private static void testTokenization() {
        TextProcessor processor = new TextProcessor();

        List<String> tokens = processor.tokenize("Java, Java! is a Search Engine.");

        assertEquals(List.of("java", "java", "search", "engine"), tokens, "tokenization should clean text");
    }

    private static void testInvertedIndexStoresFrequencyAndPositions() {
        TextProcessor processor = new TextProcessor();
        InvertedIndex index = new InvertedIndex(processor);

        index.addDocument(new Document(1, "Java Search Engine", "local://doc-1", "Java search engine Java"));

        Posting javaPosting = index.getPostingList("java").get(1);
        Posting searchPosting = index.getPostingList("search").get(1);

        assertEquals(3, javaPosting.frequency(), "java frequency should count title and content");
        assertEquals(List.of(0, 3, 6), javaPosting.positions(), "java positions should be stored");
        assertEquals(List.of(1, 4), searchPosting.positions(), "search positions should be stored");
    }

    private static void testTermFrequencySearchRanking() {
        SearchService searchService = new SearchService(sampleIndex(), new TextProcessor(), new TermFrequencyRanker());

        List<SearchResult> results = searchService.search("java search");

        assertEquals(3, results.size(), "java search should match three documents");
        assertEquals(1, results.get(0).document().id(), "term frequency should rank doc 1 first");
        assertEquals(5.0, results.get(0).score(), "doc 1 term-frequency score should be 5");
    }

    private static void testPhraseSearch() {
        SearchService searchService = new SearchService(sampleIndex(), new TextProcessor(), new TfIdfRanker());

        List<SearchResult> matchingResults = searchService.search("\"search engine\"");
        List<SearchResult> nonMatchingResults = searchService.search("\"engine search\"");

        assertEquals(1, matchingResults.size(), "search engine phrase should match one document");
        assertEquals(1, matchingResults.get(0).document().id(), "search engine phrase should match doc 1");
        assertEquals(2.0, matchingResults.get(0).score(), "phrase score should equal phrase frequency");
        assertTrue(nonMatchingResults.isEmpty(), "engine search phrase should not match");
    }

    private static void testTfIdfRewardsRarerTerms() {
        InvertedIndex index = sampleIndex();
        SearchService searchService = new SearchService(index, new TextProcessor(), new TfIdfRanker());

        List<SearchResult> javaResults = searchService.search("java");
        List<SearchResult> backendResults = searchService.search("backend");

        assertEquals(2, index.documentFrequency("java"), "java should appear in two documents");
        assertEquals(1, index.documentFrequency("backend"), "backend should appear in one document");
        assertTrue(
                backendResults.get(0).score() > javaResults.get(0).score(),
                "rarer term backend should get a higher TF-IDF score than common term java"
        );
    }

    private static void testBm25Ranking() {
        SearchService searchService = new SearchService(sampleIndex(), new TextProcessor(), new Bm25Ranker());

        List<SearchResult> results = searchService.search("java search");

        assertEquals(3, results.size(), "BM25 should return all matching documents");
        assertEquals(1, results.get(0).document().id(), "BM25 should rank doc 1 highest for java search");
        assertTrue(results.get(0).score() > 0.0, "BM25 score should be positive");
        assertTrue(results.get(0).score() > results.get(1).score(), "BM25 should produce a decreasing relevance order");
    }

    private static InvertedIndex sampleIndex() {
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

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ". Expected: " + expected + ", actual: " + actual);
        }
    }

    private static void assertEquals(double expected, double actual, String message) {
        double tolerance = 0.000001;
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ". Expected: " + expected + ", actual: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
