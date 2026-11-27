package com.searchengine.crawler;

import java.util.Optional;

/** Retrieves a URL. Implementations: {@link HttpFetcher} in production, fakes in tests. */
public interface Fetcher {

    /**
     * @param status      HTTP status (0 when the request failed before a response)
     * @param contentType value of the Content-Type header ("" if absent)
     * @param body        decoded body text, empty for non-200 or non-text responses
     */
    record Response(int status, String contentType, String body) {
        public boolean isHtml() {
            return status == 200 && (contentType.isEmpty() || contentType.toLowerCase(java.util.Locale.ROOT).contains("html"));
        }
    }

    Optional<Response> fetch(String url);
}
