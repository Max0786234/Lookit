package com.searchengine.crawler;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlerTest {

    /** In-memory web with a fake clock: sleeping advances time instantly. */
    static final class FakeWeb implements Fetcher {
        final Map<String, Response> pages = new HashMap<>();
        final List<String> requests = new ArrayList<>();
        final Map<String, List<Long>> fetchTimesByHost = new HashMap<>();
        long now;

        void page(String url, String html) {
            pages.put(url, new Response(200, "text/html; charset=utf-8", html));
        }

        @Override
        public Optional<Response> fetch(String url) {
            requests.add(url);
            if (!url.endsWith("/robots.txt")) {
                fetchTimesByHost.computeIfAbsent(UrlNormalizer.host(url), h -> new ArrayList<>()).add(now);
            }
            return Optional.of(pages.getOrDefault(url, new Response(404, "text/html", "")));
        }

        Crawler crawler() {
            return new Crawler(this, new SimHash.Index<>(3), () -> now, ms -> now += ms, "LookitBot");
        }
    }

    private static String html(String title, String body, String... links) {
        StringBuilder sb = new StringBuilder("<html><head><title>").append(title).append("</title></head><body><p>")
                .append(body).append("</p>");
        for (String l : links) {
            sb.append("<a href='").append(l).append("'>link</a>");
        }
        return sb.append("</body></html>").toString();
    }

    private static String text(int seed) {
        java.util.Random r = new java.util.Random(seed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) { // SimHash is only reliable on page-length text
            sb.append("word").append(r.nextInt(500)).append(' ');
        }
        return sb.toString();
    }

    @Test
    void crawlsBreadthFirstWithinDepthAndPageLimits() {
        FakeWeb web = new FakeWeb();
        web.page("https://a.com/", html("Home", text(1), "/one", "/two"));
        web.page("https://a.com/one", html("One", text(2), "/three"));
        web.page("https://a.com/two", html("Two", text(3)));
        web.page("https://a.com/three", html("Three", text(4)));
        List<Crawler.CrawledPage> stored = new ArrayList<>();
        Crawler.Stats stats = new Crawler.Stats();

        web.crawler().run(new Crawler.Config(List.of("https://a.com/"), 10, 1, true, Duration.ofMillis(500)),
                p -> stored.add(p), stats, () -> false);

        assertEquals(List.of("https://a.com/", "https://a.com/one", "https://a.com/two"),
                stored.stream().map(Crawler.CrawledPage::url).toList(), "depth 1 excludes /three");
        assertEquals(3, stats.stored.get());
        assertEquals(List.of("https://a.com/one", "https://a.com/two"), stored.get(0).outlinks());
    }

    @Test
    void respectsRobotsAndCrawlDelayPerHost() {
        FakeWeb web = new FakeWeb();
        web.pages.put("https://a.com/robots.txt",
                new Fetcher.Response(200, "text/plain", "User-agent: *\nDisallow: /private\nCrawl-delay: 2"));
        web.page("https://a.com/", html("Home", text(1), "/private/secret", "/p1", "/p2", "https://b.com/"));
        web.page("https://a.com/p1", html("P1", text(2)));
        web.page("https://a.com/p2", html("P2", text(3)));
        web.page("https://b.com/", html("B", text(4), "/x"));
        web.page("https://b.com/x", html("BX", text(5)));
        Crawler.Stats stats = new Crawler.Stats();

        web.crawler().run(new Crawler.Config(List.of("https://a.com/", "https://b.com/"), 20, 2, true,
                Duration.ofMillis(500)), p -> true, stats, () -> false);

        assertFalse(web.requests.contains("https://a.com/private/secret"));
        assertEquals(1, stats.robotsBlocked.get());
        assertGapsAtLeast(web.fetchTimesByHost.get("a.com"), 2000);
        assertGapsAtLeast(web.fetchTimesByHost.get("b.com"), 500);
        // hosts are interleaved: b.com is not starved while a.com waits out its crawl-delay
        assertTrue(web.fetchTimesByHost.get("b.com").get(1) < web.fetchTimesByHost.get("a.com").get(2));
    }

    @Test
    void skipsNearDuplicatesAndNoindexButFollowsTheirLinks() {
        FakeWeb web = new FakeWeb();
        String body = text(1);
        web.page("https://a.com/", html("Home", body, "/copy", "/hidden"));
        web.page("https://a.com/copy", html("Copy", "Printable version. " + body, "/deep"));
        web.page("https://a.com/hidden", "<html><head><meta name='robots' content='noindex'></head><body>"
                + text(7) + "</body></html>");
        web.page("https://a.com/deep", html("Deep", text(9)));
        List<String> stored = new ArrayList<>();
        Crawler.Stats stats = new Crawler.Stats();

        web.crawler().run(new Crawler.Config(List.of("https://a.com/"), 10, 3, true, Duration.ZERO),
                p -> stored.add(p.url()), stats, () -> false);

        assertEquals(List.of("https://a.com/", "https://a.com/deep"), stored);
        assertEquals(1, stats.duplicates.get());
        assertEquals(1, stats.noindex.get());
    }

    @Test
    void sameSiteOnlyAndCancellation() {
        FakeWeb web = new FakeWeb();
        web.page("https://a.com/", html("Home", text(1), "https://other.org/", "/next"));
        web.page("https://a.com/next", html("Next", text(2)));
        web.page("https://other.org/", html("Other", text(3)));

        Crawler.Stats stats = new Crawler.Stats();
        web.crawler().run(new Crawler.Config(List.of("https://a.com/"), 10, 2, true, Duration.ZERO),
                p -> true, stats, () -> false);
        assertFalse(web.requests.contains("https://other.org/"));

        Crawler.Stats cancelled = new Crawler.Stats();
        web.crawler().run(new Crawler.Config(List.of("https://a.com/"), 10, 2, true, Duration.ZERO),
                p -> true, cancelled, () -> true);
        assertEquals(0, cancelled.fetched.get());
    }

    private static void assertGapsAtLeast(List<Long> times, long gapMillis) {
        for (int i = 1; i < times.size(); i++) {
            assertTrue(times.get(i) - times.get(i - 1) >= gapMillis, "gap " + (times.get(i) - times.get(i - 1)));
        }
    }
}
