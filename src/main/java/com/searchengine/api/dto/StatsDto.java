package com.searchengine.api.dto;

import java.util.List;

/**
 * @param sources         where the indexed documents come from, largest first
 * @param semanticEnabled whether semantic and hybrid modes are available
 */
public record StatsDto(int totalDocuments, long totalTerms, int uniqueTerms, List<Source> sources,
                       boolean semanticEnabled) {

    public record Source(String name, long documents) {
    }
}
