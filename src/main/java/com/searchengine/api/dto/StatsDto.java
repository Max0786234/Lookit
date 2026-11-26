package com.searchengine.api.dto;

public record StatsDto(int totalDocuments, long totalTerms, int uniqueTerms) {
}
