package com.searchengine.processor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextProcessorTest {

    private final TextProcessor textProcessor = new TextProcessor();

    @Test
    void tokenizeRemovesStopWordsAndNormalizesWords() {
        List<String> tokens = textProcessor.tokenize("Java, Java! is a Search Engine.");

        assertEquals(List.of("java", "java", "search", "engine"), tokens);
    }

    @Test
    void tokenizeHandlesEmptyAndPunctuationOnlyInput() {
        assertEquals(List.of(), textProcessor.tokenize("!!!"));
        assertEquals(List.of(), textProcessor.tokenize("   "));
    }

    @Test
    void tokenizePreservesNumbersAndIgnoresStopWords() {
        List<String> tokens = textProcessor.tokenize("Index 2025 and retrieval for ranking 2.");

        assertEquals(List.of("index", "2025", "retrieval", "ranking", "2"), tokens);
    }
}
