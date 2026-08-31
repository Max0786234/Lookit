package com.searchengine.crawler;

import com.searchengine.document.DocumentEntity;
import com.searchengine.document.DocumentRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

@Service
public class WebFetcherService {
    private final DocumentRepository documentRepository;
    private final CrawlerService crawlerService;
    private final HttpClient httpClient;

    public WebFetcherService(DocumentRepository documentRepository, CrawlerService crawlerService) {
        this.documentRepository = documentRepository;
        this.crawlerService = crawlerService;
        
        try {
            // Create an SSL context that accepts all certificates (for testing with sites that have cert issues)
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            }, new java.security.SecureRandom());
            
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .sslContext(sslContext)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .version(HttpClient.Version.HTTP_2)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize HTTP client", e);
        }
    }

    /**
     * Crawl starting from a seed URL, following links up to maxDepth.
     * Stores discovered pages in the database and returns crawl stats.
     * 
     * @param seedUrl Starting URL
     * @param maxDepth Maximum link depth to follow
     * @param maxPages Maximum pages to crawl
     * @param onDocumentFound Callback when a document is successfully crawled and stored
     */
    public CrawlStats crawl(String seedUrl, int maxDepth, int maxPages, Consumer<String> onDocumentFound) {
        Set<String> visited = new HashSet<>();
        Set<String> toVisit = new HashSet<>();
        toVisit.add(normalizeUrl(seedUrl));

        int pagesAdded = 0;
        int attemptedPages = 0;
        String domain = extractDomain(seedUrl);
        
        // Wikipedia needs slower rate limiting
        long rateLimitMs = domain.contains("wikipedia") ? 2000 : 500;

        for (int depth = 0; depth <= maxDepth && !toVisit.isEmpty() && pagesAdded < maxPages; depth++) {
            Set<String> nextLevel = new HashSet<>();

            for (String url : toVisit) {
                if (pagesAdded >= maxPages) break;
                if (visited.contains(url)) continue;

                visited.add(url);
                attemptedPages++;

                try {
                    Thread.sleep(rateLimitMs); // Rate limiting: be polite to servers
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                HttpResponse<String> response = fetchPage(url);
                if (response == null || response.statusCode() != 200) {
                    continue;
                }

                String html = response.body();
                CrawlerService.CrawledPage crawled = crawlerService.crawlPage(url, html);

                if (crawled.text().isBlank() || crawled.title().isBlank()) {
                    continue;
                }

                // Store the page
                DocumentEntity doc = new DocumentEntity(
                        crawled.title(),
                        url,
                        crawled.text()
                );
                documentRepository.save(doc);
                pagesAdded++;
                System.out.println("✓ Crawled: " + crawled.title());
                
                // Notify callback
                if (onDocumentFound != null) {
                    onDocumentFound.accept(crawled.title());
                }

                // Queue discovered links for next level (same domain only)
                for (String link : crawled.links()) {
                    if (!visited.contains(link) && isSameDomain(link, domain)) {
                        nextLevel.add(link);
                    }
                }
            }

            toVisit = nextLevel;
        }

        return new CrawlStats(seedUrl, pagesAdded, attemptedPages, visited.size(), domain);
    }

    /**
     * Simpler overload without callback
     */
    public CrawlStats crawl(String seedUrl, int maxDepth, int maxPages) {
        return crawl(seedUrl, maxDepth, maxPages, null);
    }

    private HttpResponse<String> fetchPage(String url) {
        try {
            System.out.println("Fetching: " + url);
            
            // Improved User-Agent to look more like a real browser
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20)) // Longer timeout for Wikipedia
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("✓ HTTP " + response.statusCode() + ": " + url);
            
            // Only consider 200 status as success
            if (response.statusCode() == 200) {
                return response;
            } else {
                System.err.println("✗ HTTP " + response.statusCode() + " from " + url);
                return null;
            }
        } catch (java.net.http.HttpTimeoutException e) {
            System.err.println("✗ Timeout: " + url + " (exceeded 20s)");
            return null;
        } catch (java.net.ConnectException e) {
            System.err.println("✗ Connection error: " + url + " (" + e.getMessage() + ")");
            return null;
        } catch (Exception e) {
            System.err.println("✗ Error fetching " + url + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }

    private String normalizeUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        try {
            return new URI(url).normalize().toString();
        } catch (Exception e) {
            return url;
        }
    }

    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isSameDomain(String url, String domain) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host != null && host.endsWith(domain);
        } catch (Exception e) {
            return false;
        }
    }

    public record CrawlStats(
            String seedUrl,
            int pagesAdded,
            int attemptedPages,
            int uniquesVisited,
            String domain
    ) {
    }
}
