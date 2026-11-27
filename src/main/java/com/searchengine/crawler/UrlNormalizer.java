package com.searchengine.crawler;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Canonicalises URLs so the same page is not crawled twice under different spellings.
 * Lower-cases scheme and host, drops fragments and default ports, and resolves "." / ".." segments.
 */
public final class UrlNormalizer {
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:(?!\\d)");

    private UrlNormalizer() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String url = raw.trim();
        // "mailto:x", "javascript:y" have a scheme; "example.com:8080/x" does not (digit after colon).
        if (!url.contains("://") && !SCHEME.matcher(url).find()) {
            url = "https://" + url;
        }
        try {
            URI uri = new URI(url).normalize();
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return Optional.empty();
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return Optional.empty();
            }
            host = host.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if ((port == 80 && scheme.equals("http")) || (port == 443 && scheme.equals("https"))) {
                port = -1;
            }
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            URI normalized = new URI(scheme, null, host, port, null, null, null);
            StringBuilder sb = new StringBuilder(normalized.toString()).append(path);
            if (uri.getRawQuery() != null) {
                sb.append('?').append(uri.getRawQuery());
            }
            return Optional.of(sb.toString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    /** True when {@code host} equals {@code domain} or is a sub-domain of it (dot-boundary checked). */
    public static boolean isSameSite(String host, String domain) {
        if (host == null || domain == null || host.isEmpty() || domain.isEmpty()) {
            return false;
        }
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
