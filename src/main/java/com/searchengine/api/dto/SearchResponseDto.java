package com.searchengine.api.dto;

import java.util.List;

/** {@code totalIsLowerBound}: dynamic pruning skipped documents, so {@code total} means "at least". */
public record SearchResponseDto(String query, long total, boolean totalIsLowerBound, int page, int size, long tookMs,
                                List<SearchResultDto> results) {
}
