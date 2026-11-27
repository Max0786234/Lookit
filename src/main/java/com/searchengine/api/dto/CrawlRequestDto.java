package com.searchengine.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** {@code sameSiteOnly} defaults to true: follow links only within the seed's site. */
public record CrawlRequestDto(
        @NotBlank @Pattern(regexp = "(?i)^(https?://)?[^\s/$.?#][^\s]*$", message = "must be an http(s) URL") String seedUrl,
        @Min(1) @Max(500) int maxPages,
        @Min(0) @Max(5) int maxDepth,
        Boolean sameSiteOnly) {
}
