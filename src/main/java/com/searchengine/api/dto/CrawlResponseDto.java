package com.searchengine.api.dto;

import java.util.List;

public record CrawlResponseDto(String seedUrl, int crawledPages, List<String> urls) {
}
