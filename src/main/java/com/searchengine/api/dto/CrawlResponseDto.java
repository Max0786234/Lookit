package com.searchengine.api.dto;

import java.time.Instant;

/** Status of an asynchronous crawl job. */
public record CrawlResponseDto(String jobId, String seedUrl, String status, int fetched, int stored, int duplicates,
                               int robotsBlocked, int errors, int queued, Instant startedAt, Instant finishedAt,
                               String error) {
}
