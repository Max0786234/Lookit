package com.searchengine.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnalyzerTest {

    private final Analyzer english = Analyzer.english();

    @Test
    void stemsRemovesStopwordsAndKeepsPositionGaps() {
        List<Token> tokens = english.analyze("The engines of a Search Engine");

        assertEquals(List.of("engin", "search", "engin"), tokens.stream().map(Token::term).toList());
        // "the"=0, "engines"=1, "of"=2, "a"=3, "search"=4, "engine"=5
        assertEquals(List.of(1, 4, 5), tokens.stream().map(Token::position).toList());
    }

    @Test
    void offsetsPointIntoOriginalText() {
        String text = "Hello, Wörld!";
        Token world = Analyzer.simple().analyze(text).get(1);

        assertEquals("world", world.term());
        assertEquals("Wörld", text.substring(world.start(), world.end()));
    }

    @Test
    void foldsLatinAccentsButKeepsOtherScriptsIntact() {
        assertEquals(List.of("cafe", "naive", "strasse"), Analyzer.simple().terms("Café naïve Straße"));
        assertEquals(List.of("नमस्ते", "दुनिया"), Analyzer.simple().terms("नमस्ते दुनिया"));
    }

    @Test
    void cjkIsSplitIntoUnigrams() {
        assertEquals(List.of("搜", "索", "java"), Analyzer.simple().terms("搜索 Java"));
    }

    @Test
    void possessivesAndApostrophes() {
        assertEquals(List.of("porter", "dont"), Analyzer.simple().terms("Porter's don't"));
    }

    @Test
    void numbersAreKeptAndPunctuationIgnored() {
        assertEquals(List.of("index", "2025", "retriev", "rank", "2"),
                english.terms("Index 2025 and retrieval for ranking 2."));
        assertEquals(List.of(), english.terms("!!! ..."));
    }

    @Test
    void normalizeSingleWord() {
        assertEquals("engin", english.normalize("Engines"));
        assertNull(english.normalize("the"));
    }
}
