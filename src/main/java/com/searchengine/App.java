package com.searchengine;

import com.searchengine.document.Document;
import com.searchengine.index.InvertedIndex;
import com.searchengine.processor.TextProcessor;
import com.searchengine.query.SearchResult;
import com.searchengine.query.SearchService;
import com.searchengine.ranking.Bm25Ranker;
import com.searchengine.ranking.TermFrequencyRanker;
import com.searchengine.ranking.TfIdfRanker;

import java.util.List;

public class App {
    public static void main(String[] args) {
        TextProcessor textProcessor = new TextProcessor();
        InvertedIndex index = new InvertedIndex(textProcessor);
        SearchService termFrequencySearch = new SearchService(index, textProcessor, new TermFrequencyRanker());
        SearchService tfIdfSearch = new SearchService(index, textProcessor, new TfIdfRanker());
        SearchService bm25Search = new SearchService(index, textProcessor, new Bm25Ranker());

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

        printIndexEntry(index, "java");
        printIndexEntry(index, "search");
        printIndexEntry(index, "engine");

        runQuery("Term Frequency", termFrequencySearch, "java search");
        runQuery("TF-IDF", tfIdfSearch, "java search");
        runQuery("BM25", bm25Search, "java search");
        runQuery("TF-IDF", tfIdfSearch, "backend api");
        runQuery("BM25", bm25Search, "backend api");
        runQuery("TF-IDF", tfIdfSearch, "ranking index");
        runQuery("Phrase Search", tfIdfSearch, "\"search engine\"");
        runQuery("Phrase Search", tfIdfSearch, "\"engine search\"");
    }

    private static void printIndexEntry(InvertedIndex index, String term) {
        System.out.println();
        System.out.println("Index entry for term: " + term);

        index.getPostingList(term).forEach((documentId, posting) -> System.out.printf(
                "- docId=%d frequency=%d positions=%s%n",
                documentId,
                posting.frequency(),
                posting.positions()
        ));
    }

    private static void runQuery(String mode, SearchService searchService, String query) {
        System.out.println();
        System.out.println(mode + " query: " + query);
        System.out.println("Results:");

        List<SearchResult> results = searchService.search(query);

        if (results.isEmpty()) {
            System.out.println("No results found.");
            return;
        }

        for (SearchResult result : results) {
            System.out.printf(
                    "- score=%.2f title=\"%s\" url=%s%n",
                    result.score(),
                    result.document().title(),
                    result.document().url()
            );
        }
    }
}
