package com.searchengine.search;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnippetGeneratorTest {

    private final SnippetGenerator generator =
            new SnippetGenerator(w -> w.toLowerCase(Locale.ROOT), 6);

    @Test
    void picksWindowCoveringMostQueryTermsAndHighlightsThem() {
        String text = "Alpha beta gamma delta. Lots of filler words here and there. "
                + "The inverted index makes search fast. More filler follows.";

        SnippetGenerator.Snippet s = generator.generate(text, Set.of("inverted", "search"));

        assertTrue(s.text().contains("inverted index makes search"), s.text());
        assertTrue(s.text().startsWith("… "));
        assertEquals(2, s.highlights().size());
        SnippetGenerator.Highlight first = s.highlights().get(0);
        assertEquals("inverted", s.text().substring(first.start(), first.end()));
    }

    @Test
    void noMatchesFallsBackToLeadingWindow() {
        SnippetGenerator.Snippet s = generator.generate("one two three four five six seven", Set.of("zzz"));
        assertEquals("one two three four five six …", s.text());
        assertTrue(s.highlights().isEmpty());
    }

    @Test
    void shortTextIsReturnedWhole() {
        SnippetGenerator.Snippet s = generator.generate("search engine", Set.of("engine"));
        assertEquals("search engine", s.text());
        assertEquals(1, s.highlights().size());
    }
}
