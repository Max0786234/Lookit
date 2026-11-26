package com.searchengine.api.dto;

import java.util.List;

public record SearchResponseDto(String query, long total, int page, int size, long tookMs,
                                List<SearchResultDto> results) {
}
