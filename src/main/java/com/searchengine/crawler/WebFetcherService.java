package com.searchengine.crawler;

import com.searchengine.document.DocumentEntity;
import com.searchengine.document.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Breadth-first, same-site crawler. Phase 3 replaces this with an async, multi-host frontier;
 * this version fixes the correctness/security problems of the original:
 * TLS is verified, compressed bodies are decoded, the bot identifies itself honestly,
 * sub-domain matching is dot-boundary safe, and already-stored URLs are skipped.
 */
@Service
public class WebFetcherService {
    private static final Logger log = LoggerFactory.getLogger(WebFetcherService.class);
    static final String USER_AGENT = "LookitBot/0.2 (+https://github.com/Max0786234/Lookit)";
    private static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

    private final DocumentRepository documentRepository;
    private final CrawlerService crawlerService;
    private final HttpClient httpClient;

    public WebFetcherService(DocumentRepository documentRepository, CrawlerService crawlerService) {
        this.documentRepository = documentRepository;
        this.crawlerService = crawlerService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CrawlStats crawl(String seedUrl, int maxDepth, int maxPages, Consumer<String> onDocumentFound) {
        String seed = UrlNormalizer.normalize(seedUrl)
                .orElseThrow(() -> new IllegalArgumentException("Invalid seed URL: " + seedUrl));
        String domain = UrlNormalizer.host(seed);

        Set<String> seen = new HashSet<>();
        Deque<String[]> queue = new ArrayDeque<>(); // [url, depth]
        queue.add(new String[]{seed, "0"});
        seen.add(seed);

        int pagesAdded = 0;
        int attempted = 0;
        long delayMs = 500;

        while (!queue.isEmpty() && pagesAdded < maxPages) {
            String[] next = queue.poll();
            String url = next[0];
            int depth = Integer.parseInt(next[1]);

            if (attempted > 0 && !sleep(delayMs)) {
                break;
            }
            attempted++;

            Optional<String> html = fetch(url);
            if (html.isEmpty()) {
                continue;
            }
            CrawlerService.CrawledPage page = crawlerService.crawlPage(url, html.get());

            if (!page.text().isBlank() && !page.title().isBlank() && !documentRepository.existsByUrl(url)) {
                documentRepository.save(new DocumentEntity(page.title(), url, page.text()));
                pagesAdded++;
                log.info("Indexed {} ({})", page.title(), url);
                if (onDocumentFound != null) {
                    onDocumentFound.accept(page.title());
                }
            }

            if (depth < maxDepth) {
                for (String link : page.links()) {
                    UrlNormalizer.normalize(link)
                            .filter(u -> UrlNormalizer.isSameSite(UrlNormalizer.host(u), domain))
                            .filter(seen::add)
                            .ifPresent(u -> queue.add(new String[]{u, String.valueOf(depth + 1)}));
                }
            }
        }

        return new CrawlStats(seed, pagesAdded, attempted, seen.size(), domain);
    }

    public CrawlStats crawl(String seedUrl, int maxDepth, int maxPages) {
        return crawl(seedUrl, maxDepth, maxPages, null);
    }

    Optional<String> fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
                    .header("Accept-Encoding", "gzip, deflate")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream raw = response.body()) {
                if (response.statusCode() != 200) {
                    log.debug("HTTP {} from {}", response.statusCode(), url);
                    return Optional.empty();
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (!contentType.isEmpty() && !contentType.toLowerCase(Locale.ROOT).contains("html")) {
                    log.debug("Skipping non-HTML {} ({})", url, contentType);
                    return Optional.empty();
                }
                String encoding = response.headers().firstValue("Content-Encoding").orElse("");
                byte[] body = decode(raw.readNBytes(MAX_BODY_BYTES), encoding);
                return Optional.of(new String(body, charsetOf(contentType)));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Fetch failed for {}: {}", url, e.toString());
            return Optional.empty();
        }
    }

    /** Java's HttpClient does not transparently decompress, so we do it here. */
    static byte[] decode(byte[] body, String contentEncoding) throws IOException {
        String enc = contentEncoding.toLowerCase(Locale.ROOT).trim();
        if (enc.contains("gzip")) {
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return in.readNBytes(MAX_BODY_BYTES * 4);
            }
        }
        if (enc.contains("deflate")) {
            try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(body))) {
                return in.readNBytes(MAX_BODY_BYTES * 4);
            }
        }
        return body;
    }

    static Charset charsetOf(String contentType) {
        for (String part : contentType.split(";")) {
            String p = part.trim();
            if (p.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                try {
                    return Charset.forName(p.substring(8).replace("\"", "").trim());
                } catch (Exception ignored) {
                    // fall through to UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public record CrawlStats(String seedUrl, int pagesAdded, int attemptedPages, int uniquesVisited, String domain) {
    }
}
