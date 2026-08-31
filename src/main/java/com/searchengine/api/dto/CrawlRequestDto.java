package com.searchengine.api.dto;

public record CrawlRequestDto(String seedUrl, int maxPages, int maxDepth) {
}
