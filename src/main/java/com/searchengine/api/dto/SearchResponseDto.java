package com.searchengine.api.dto;

import java.util.List;

/**
 * @param totalIsLowerBound dynamic pruning / top-k retrieval skipped documents, so {@code total} means "at least"
 * @param didYouMean        spelling-corrected query when the query has unknown words, else null
 */
public record SearchResponseDto(String query, String mode, long total, boolean totalIsLowerBound, int page, int size,
                                long tookMs, String didYouMean, List<SearchResultDto> results) {
}
