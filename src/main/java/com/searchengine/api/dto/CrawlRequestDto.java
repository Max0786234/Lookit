package com.searchengine.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CrawlRequestDto(
        @NotBlank String seedUrl,
        @Min(1) @Max(100) int maxPages,
        @Min(0) @Max(3) int maxDepth) {
}
