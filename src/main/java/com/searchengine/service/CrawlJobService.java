package com.searchengine.service;

import com.searchengine.crawler.Crawler;
import com.searchengine.crawler.Fetcher;
import com.searchengine.crawler.HttpFetcher;
import com.searchengine.crawler.PageRank;
import com.searchengine.crawler.SimHash;
import com.searchengine.crawler.UrlNormalizer;
import com.searchengine.document.Document;
import com.searchengine.document.DocumentRepository;
import com.searchengine.document.LinkEntity;
import com.searchengine.document.LinkRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs crawls in the background (one virtual thread per job), stores pages and their links,
 * and recomputes PageRank over the stored link graph when a crawl finishes.
 */
@Service
public class CrawlJobService {
    private static final Logger log = LoggerFactory.getLogger(CrawlJobService.class);

    public enum Status { RUNNING, DONE, CANCELLED, FAILED }

    /** Mutable job state; counters are live while the crawl runs. */
    public static final class Job {
        public final String id = UUID.randomUUID().toString();
        public final String seedUrl;
        public final Crawler.Stats stats = new Crawler.Stats();
        public final Instant startedAt = Instant.now();
        volatile Status status = Status.RUNNING;
        volatile Instant finishedAt;
        volatile String error;
        volatile boolean cancelRequested;

        Job(String seedUrl) {
            this.seedUrl = seedUrl;
        }

        public Status status() {
            return status;
        }

        public Instant finishedAt() {
            return finishedAt;
        }

        public String error() {
            return error;
        }
    }

    private final SearchEngineService search;
    private final DocumentRepository documents;
    private final LinkRepository links;
    private final Fetcher fetcher;
    private final Duration minDelay;
    private final double pageRankWeight;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile SimHash.Index<String> fingerprints;

    @Autowired
    public CrawlJobService(SearchEngineService search, DocumentRepository documents, LinkRepository links,
                           @Value("${lookit.crawler.min-delay-ms:1000}") long minDelayMs,
                           @Value("${lookit.ranking.pagerank-weight:0.3}") double pageRankWeight) {
        this(search, documents, links, new HttpFetcher(), Duration.ofMillis(minDelayMs), pageRankWeight);
    }

    CrawlJobService(SearchEngineService search, DocumentRepository documents, LinkRepository links, Fetcher fetcher,
                    Duration minDelay, double pageRankWeight) {
        this.search = search;
        this.documents = documents;
        this.links = links;
        this.fetcher = fetcher;
        this.minDelay = minDelay;
        this.pageRankWeight = pageRankWeight;
    }

    public Job start(String seedUrl, int maxPages, int maxDepth, boolean sameSiteOnly) {
        String seed = UrlNormalizer.normalize(seedUrl)
                .orElseThrow(() -> new IllegalArgumentException("Invalid seed URL: " + seedUrl));
        Job job = new Job(seed);
        jobs.put(job.id, job);
        executor.submit(() -> run(job, new Crawler.Config(List.of(seed), maxPages, maxDepth, sameSiteOnly, minDelay)));
        return job;
    }

    /** Runs synchronously on the calling thread; used by {@link #start} and tests. */
    void run(Job job, Crawler.Config config) {
        try {
            Crawler crawler = new Crawler(fetcher, fingerprints());
            crawler.run(config, page -> store(page), job.stats, () -> job.cancelRequested);
            search.reindexAllDocuments();
            updatePageRank();
            job.status = job.cancelRequested ? Status.CANCELLED : Status.DONE;
        } catch (RuntimeException e) {
            log.warn("Crawl {} failed", job.id, e);
            job.error = e.getMessage();
            job.status = Status.FAILED;
        } finally {
            job.finishedAt = Instant.now();
            log.info("Crawl {} {}: fetched {}, stored {}, duplicates {}, robots-blocked {}", job.id, job.status,
                    job.stats.fetched.get(), job.stats.stored.get(), job.stats.duplicates.get(),
                    job.stats.robotsBlocked.get());
        }
    }

    private boolean store(Crawler.CrawledPage page) {
        if (documents.existsByUrl(page.url())) {
            return false;
        }
        Document doc = search.addDocument(page.title(), page.url(), page.text());
        List<LinkEntity> out = new ArrayList<>();
        for (String target : page.outlinks()) {
            out.add(new LinkEntity(doc.id(), target));
        }
        links.saveAll(out);
        return true;
    }

    /** Fingerprints of everything already stored, built once, so re-crawls skip known content. */
    private synchronized SimHash.Index<String> fingerprints() {
        if (fingerprints == null) {
            SimHash.Index<String> index = new SimHash.Index<>(3);
            for (Document d : search.getAllDocuments()) {
                index.add(SimHash.fingerprint(d.content()), d.url());
            }
            fingerprints = index;
        }
        return fingerprints;
    }

    /** Recomputes PageRank over stored documents and installs it as the engine's static prior. */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void updatePageRank() {
        Map<String, Integer> idByUrl = new HashMap<>();
        for (Document d : search.getAllDocuments()) {
            idByUrl.put(d.url(), d.id());
        }
        List<PageRank.Edge> edges = new ArrayList<>();
        for (LinkEntity l : links.findAll()) {
            Integer to = idByUrl.get(l.getToUrl());
            if (to != null) {
                edges.add(new PageRank.Edge(l.getFromDocId(), to));
            }
        }
        if (edges.isEmpty()) {
            return;
        }
        int[] nodes = idByUrl.values().stream().mapToInt(Integer::intValue).toArray();
        PageRank.Result pr = PageRank.compute(nodes, edges, 1e-6, 100);
        Map<Integer, Double> normalized = new HashMap<>();
        for (int id : nodes) {
            normalized.put(id, pr.normalized(id));
        }
        search.engine().setStaticPrior(normalized, pageRankWeight);
        log.info("PageRank over {} docs / {} links converged in {} iterations", nodes.length, edges.size(), pr.iterations());
    }

    public Optional<Job> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public Collection<Job> list() {
        return jobs.values().stream().sorted(Comparator.comparing((Job j) -> j.startedAt).reversed()).toList();
    }

    public boolean cancel(String id) {
        Job job = jobs.get(id);
        if (job == null || job.status != Status.RUNNING) {
            return false;
        }
        job.cancelRequested = true;
        return true;
    }

    @PreDestroy
    void shutdown() {
        jobs.values().forEach(j -> j.cancelRequested = true);
        executor.shutdown();
    }
}
