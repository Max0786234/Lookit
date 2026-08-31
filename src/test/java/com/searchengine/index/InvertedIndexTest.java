package com.searchengine.index;

import com.searchengine.document.Document;
import com.searchengine.processor.TextProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvertedIndexTest {

    private final TextProcessor textProcessor = new TextProcessor();

    @Test
    void addDocumentStoresDocumentAndPostingPositions() {
        InvertedIndex index = new InvertedIndex(textProcessor);

        index.addDocument(new Document(1, "Java Search Engine", "https://example.com/java-search", "Java search engine Java"));

        assertEquals(1, index.documentCount());
        assertEquals(1, index.documentFrequency("java"));
        assertEquals(1, index.documentFrequency("search"));
        assertEquals(7, index.documentLength(1));

        Posting javaPosting = index.getPostingList("java").get(1);
        assertEquals(List.of(0, 3, 6), javaPosting.positions());
    }

    @Test
    void getDocumentAndAverageLengthReflectIndexState() {
        InvertedIndex index = new InvertedIndex(textProcessor);
        index.addDocument(new Document(10, "Java", "https://example.com/one", "java"));
        index.addDocument(new Document(11, "Search Engine", "https://example.com/two", "search engine"));

        Optional<Document> stored = index.getDocument(10);
        assertTrue(stored.isPresent());
        assertEquals("Java", stored.get().title());
        assertEquals(4, index.documentLength(11));
        assertEquals(3.0, index.averageDocumentLength(), 0.0001);
        assertEquals(3, index.vocabularySize());
    }
}
