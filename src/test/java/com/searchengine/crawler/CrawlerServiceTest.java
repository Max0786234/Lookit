package com.searchengine.crawler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlerServiceTest {

    @Test
    void parseHtmlExtractsTitleContentAndOutgoingLinks() {
        String url = "https://example.com/";
        String html = "" +
                "<html><head><title>Example Page</title></head>" +
                "<body><h1>Hello World</h1>" +
                "<p>Java search engine crawler example.</p>" +
                "<a href=\"/about\">About</a>" +
                "<a href=\"https://example.com/contact\">Contact</a>" +
                "</body></html>";

        CrawlerService.CrawledPage page = new CrawlerService().crawlPage(url, html);

        assertEquals("Example Page", page.title());
        assertTrue(page.text().toLowerCase(Locale.ROOT).contains("java"));
        assertTrue(page.links().contains("https://example.com/about"));
        assertTrue(page.links().contains("https://example.com/contact"));
        assertEquals(2, page.links().size());
    }
}
