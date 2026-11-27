package com.searchengine.service;

import com.searchengine.crawler.Crawler;
import com.searchengine.crawler.Fetcher;
import com.searchengine.document.DocumentRepository;
import com.searchengine.document.LinkRepository;
import com.searchengine.loader.DocumentLoaderService;
import com.searchengine.search.SearchEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class CrawlJobServiceTest {

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private LinkRepository links;

    private static String page(String title, String body, String... hrefs) {
        StringBuilder sb = new StringBuilder("<html><head><title>" + title + "</title></head><body><p>" + body + "</p>");
        for (String h : hrefs) {
            sb.append("<a href='").append(h).append("'>x</a>");
        }
        return sb.append("</body></html>").toString();
    }

    private static String words(int seed, String extra) {
        Random r = new Random(seed);
        StringBuilder sb = new StringBuilder(extra).append(' ');
        for (int i = 0; i < 300; i++) {
            sb.append("filler").append(r.nextInt(400)).append(' ');
        }
        return sb.toString();
    }

    @Test
    void crawlStoresPagesAndLinksAndPageRankPromotesTheHub() {
        Map<String, Fetcher.Response> web = new HashMap<>();
        // hub and leaf both match "okapi" equally; every page links to the hub
        web.put("https://site.test/", new Fetcher.Response(200, "text/html", page("Home", words(1, "start"),
                "/hub", "/leaf", "/a", "/b")));
        web.put("https://site.test/hub", new Fetcher.Response(200, "text/html", page("Okapi facts", words(2, "okapi"), "/")));
        web.put("https://site.test/leaf", new Fetcher.Response(200, "text/html", page("Okapi facts", words(3, "okapi"))));
        web.put("https://site.test/a", new Fetcher.Response(200, "text/html", page("A", words(4, "a"), "/hub")));
        web.put("https://site.test/b", new Fetcher.Response(200, "text/html", page("B", words(5, "b"), "/hub")));
        Fetcher fetcher = url -> Optional.of(web.getOrDefault(url, new Fetcher.Response(404, "text/plain", "")));

        SearchEngineService search = new SearchEngineService(documents, new DocumentLoaderService(documents), "");
        CrawlJobService service = new CrawlJobService(search, documents, links, fetcher, Duration.ZERO, 1.0);
        CrawlJobService.Job job = new CrawlJobService.Job("https://site.test/");
        service.run(job, new Crawler.Config(List.of("https://site.test/"), 20, 2, true, Duration.ZERO));

        assertEquals(CrawlJobService.Status.DONE, job.status());
        assertEquals(5, job.stats.stored.get());
        assertTrue(links.count() >= 7);

        SearchEngine.Results results = search.search("okapi", 1, 10);
        assertEquals("https://site.test/hub", results.hits().get(0).document().url(),
                "hub has 3 in-links, leaf has 1: PageRank breaks the text tie");
        assertTrue(search.engine().staticPrior().normalized().size() >= 5);

        // a second crawl of the same site stores nothing new (URLs known, content fingerprinted)
        CrawlJobService.Job again = new CrawlJobService.Job("https://site.test/");
        service.run(again, new Crawler.Config(List.of("https://site.test/"), 20, 2, true, Duration.ZERO));
        assertEquals(0, again.stats.stored.get());
    }
}
