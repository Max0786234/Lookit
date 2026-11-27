package com.searchengine.crawler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlExtractorTest {

    private final HtmlExtractor extractor = new HtmlExtractor();

    @Test
    void extractsTitleMainTextAndNormalisedLinks() {
        String html = "<html><head><title> Example  Page </title></head><body>"
                + "<nav><a href='/menu'>Menu</a> navigation junk</nav>"
                + "<main><h1>Hello</h1><p>Java search engine crawler example.[1]</p>"
                + "<a href='/about#team'>About</a> <a href='https://example.com/contact'>Contact</a>"
                + "<a href='/ad' rel='nofollow sponsored'>Ad</a><a href='mailto:x@y.z'>mail</a></main>"
                + "<script>var tracking = 1;</script></body></html>";

        HtmlExtractor.Page page = extractor.extract("https://example.com/", html);

        assertEquals("Example Page", page.title());
        assertTrue(page.text().contains("Java search engine crawler example."));
        assertFalse(page.text().contains("[1]"));
        assertFalse(page.text().contains("tracking"));
        assertFalse(page.text().contains("navigation junk"));
        assertEquals(List.of("https://example.com/menu", "https://example.com/about", "https://example.com/contact"),
                page.links());
    }

    @Test
    void honoursRobotsMetaTag() {
        HtmlExtractor.Page page = extractor.extract("https://example.com/",
                "<html><head><meta name='robots' content='noindex, nofollow'></head>"
                        + "<body><a href='/x'>x</a>text</body></html>");
        assertTrue(page.noindex());
        assertTrue(page.nofollow());
        assertTrue(page.links().isEmpty());
    }

    @Test
    void titleFallsBackToHeadingThenUrl() {
        assertEquals("Heading", extractor.extract("https://e.com/a", "<h1>Heading</h1><p>x</p>").title());
        assertEquals("my page", extractor.extract("https://e.com/docs/my-page.html", "<p>x</p>").title());
    }
}
