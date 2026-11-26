package com.searchengine.api.dto;

import java.util.List;

/** One hit. {@code highlights} are [start, end) character offsets into {@code snippet}. */
public record SearchResultDto(int id, String title, String url, String snippet, List<int[]> highlights, double score) {
}
