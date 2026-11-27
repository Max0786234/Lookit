package com.searchengine.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Locale;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * java.net.http based fetcher: verified TLS, honest User-Agent, redirects followed, gzip/deflate
 * decoded (HttpClient does not do it), bodies capped at 5 MB.
 */
public final class HttpFetcher implements Fetcher {
    private static final Logger log = LoggerFactory.getLogger(HttpFetcher.class);
    public static final String USER_AGENT = "LookitBot/0.3 (+https://github.com/Max0786234/Lookit)";
    /** Token matched against robots.txt User-agent lines. */
    public static final String ROBOTS_TOKEN = "LookitBot";
    static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public Optional<Response> fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,text/plain;q=0.8,*/*;q=0.5")
                    .header("Accept-Encoding", "gzip, deflate")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream raw = response.body()) {
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (response.statusCode() != 200) {
                    return Optional.of(new Response(response.statusCode(), contentType, ""));
                }
                String lower = contentType.toLowerCase(Locale.ROOT);
                if (!lower.isEmpty() && !lower.contains("html") && !lower.contains("text/plain")) {
                    return Optional.of(new Response(200, contentType, ""));
                }
                String encoding = response.headers().firstValue("Content-Encoding").orElse("");
                byte[] body = decode(raw.readNBytes(MAX_BODY_BYTES), encoding);
                return Optional.of(new Response(200, contentType, new String(body, charsetOf(contentType))));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Fetch failed for {}: {}", url, e.toString());
            return Optional.empty();
        }
    }

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
                    // unknown charset: fall back to UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
