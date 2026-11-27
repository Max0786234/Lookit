package com.searchengine.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Polite breadth-first crawler.
 *
 * <p>The frontier keeps one FIFO queue per host and always serves the host whose politeness delay
 * expires first, so a crawl spanning several hosts never hits one host faster than
 * {@code max(minDelay, robots Crawl-delay)} while the others are idle. robots.txt is fetched once
 * per host and enforced before every request. Near-duplicate pages (SimHash distance ≤ 3) are not
 * stored but their links are still followed.
 */
public final class Crawler {
    private static final Logger log = LoggerFactory.getLogger(Crawler.class);
    static final Duration MAX_CRAWL_DELAY = Duration.ofSeconds(30);

    public record Config(List<String> seeds, int maxPages, int maxDepth, boolean sameSiteOnly, Duration minDelay) {
    }

    public record CrawledPage(String url, String title, String text, List<String> outlinks, int depth) {
    }

    /** Receives every fetched, indexable, non-duplicate page; returns whether it was stored. */
    public interface Sink {
        boolean accept(CrawledPage page);
    }

    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** Live counters, readable while the crawl runs. */
    public static final class Stats {
        public final AtomicInteger fetched = new AtomicInteger();
        public final AtomicInteger stored = new AtomicInteger();
        public final AtomicInteger duplicates = new AtomicInteger();
        public final AtomicInteger robotsBlocked = new AtomicInteger();
        public final AtomicInteger errors = new AtomicInteger();
        public final AtomicInteger noindex = new AtomicInteger();
        public final AtomicInteger queued = new AtomicInteger();
    }

    private final Fetcher fetcher;
    private final HtmlExtractor extractor = new HtmlExtractor();
    private final SimHash.Index<String> duplicates;
    private final LongSupplier clockMillis;
    private final Sleeper sleeper;
    private final String robotsAgent;

    public Crawler(Fetcher fetcher, SimHash.Index<String> duplicates) {
        this(fetcher, duplicates, System::currentTimeMillis, Thread::sleep, HttpFetcher.ROBOTS_TOKEN);
    }

    public Crawler(Fetcher fetcher, SimHash.Index<String> duplicates, LongSupplier clockMillis, Sleeper sleeper,
                   String robotsAgent) {
        this.fetcher = fetcher;
        this.duplicates = duplicates;
        this.clockMillis = clockMillis;
        this.sleeper = sleeper;
        this.robotsAgent = robotsAgent;
    }

    private record Item(String url, int depth) {
    }

    private static final class Host {
        final String name;
        final ArrayDeque<Item> queue = new ArrayDeque<>();
        RobotsTxt robots;
        long delayMillis;
        long nextAllowedAt;

        Host(String name) {
            this.name = name;
        }
    }

    public void run(Config config, Sink sink, Stats stats, BooleanSupplier cancelled) {
        Map<String, Host> hosts = new HashMap<>();
        PriorityQueue<Host> ready = new PriorityQueue<>(Comparator.comparingLong(h -> h.nextAllowedAt));
        Set<String> seen = new HashSet<>();
        Set<String> seedSites = new HashSet<>();

        for (String seed : config.seeds()) {
            UrlNormalizer.normalize(seed).ifPresent(url -> {
                seedSites.add(UrlNormalizer.host(url));
                enqueue(url, 0, hosts, ready, seen, stats);
            });
        }

        while (!ready.isEmpty() && stats.fetched.get() < config.maxPages() && !cancelled.getAsBoolean()) {
            Host host = ready.poll();
            Item item = host.queue.poll();
            if (host.robots == null) {
                host.robots = fetchRobots(host.name, item.url());
                long robotsDelay = (long) (host.robots.crawlDelaySeconds().orElse(0) * 1000);
                host.delayMillis = Math.min(Math.max(config.minDelay().toMillis(), robotsDelay), MAX_CRAWL_DELAY.toMillis());
            }
            if (!host.robots.isAllowed(item.url())) {
                stats.robotsBlocked.incrementAndGet();
                requeue(host, ready);
                continue;
            }
            if (!waitUntil(host.nextAllowedAt)) {
                break;
            }
            Fetcher.Response response = fetcher.fetch(item.url()).orElse(null);
            host.nextAllowedAt = clockMillis.getAsLong() + host.delayMillis;
            stats.fetched.incrementAndGet();
            requeue(host, ready);

            if (response == null || !response.isHtml() || response.body().isEmpty()) {
                stats.errors.incrementAndGet();
                continue;
            }
            HtmlExtractor.Page page;
            try {
                page = extractor.extract(item.url(), response.body());
            } catch (RuntimeException e) {
                log.debug("Unparseable page {}: {}", item.url(), e.toString());
                stats.errors.incrementAndGet();
                continue;
            }
            if (item.depth() < config.maxDepth()) {
                for (String link : page.links()) {
                    if (!config.sameSiteOnly() || seedSites.stream().anyMatch(s -> UrlNormalizer.isSameSite(UrlNormalizer.host(link), s))) {
                        enqueue(link, item.depth() + 1, hosts, ready, seen, stats);
                    }
                }
            }
            if (page.noindex()) {
                stats.noindex.incrementAndGet();
                continue;
            }
            if (page.text().isBlank()) {
                continue;
            }
            long fp = SimHash.fingerprint(page.text());
            if (duplicates.findNear(fp).isPresent()) {
                stats.duplicates.incrementAndGet();
                continue;
            }
            if (sink.accept(new CrawledPage(item.url(), page.title(), page.text(), page.links(), item.depth()))) {
                duplicates.add(fp, item.url());
                stats.stored.incrementAndGet();
            }
        }
    }

    private void enqueue(String url, int depth, Map<String, Host> hosts, PriorityQueue<Host> ready, Set<String> seen,
                         Stats stats) {
        if (!seen.add(url)) {
            return;
        }
        String name = UrlNormalizer.host(url);
        Host host = hosts.computeIfAbsent(name, Host::new);
        boolean wasIdle = host.queue.isEmpty();
        host.queue.add(new Item(url, depth));
        stats.queued.incrementAndGet();
        if (wasIdle && !ready.contains(host)) {
            ready.add(host);
        }
    }

    private static void requeue(Host host, PriorityQueue<Host> ready) {
        if (!host.queue.isEmpty()) {
            ready.add(host); // re-inserted with its (possibly updated) nextAllowedAt
        }
    }

    private RobotsTxt fetchRobots(String host, String sampleUrl) {
        String scheme = sampleUrl.startsWith("http://") ? "http" : "https";
        Fetcher.Response r = fetcher.fetch(scheme + "://" + host + "/robots.txt").orElse(null);
        if (r == null) {
            return RobotsTxt.forStatus(0);
        }
        return r.status() == 200 ? RobotsTxt.parse(r.body(), robotsAgent) : RobotsTxt.forStatus(r.status());
    }

    private boolean waitUntil(long at) {
        long wait = at - clockMillis.getAsLong();
        if (wait <= 0) {
            return true;
        }
        try {
            sleeper.sleep(wait);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
