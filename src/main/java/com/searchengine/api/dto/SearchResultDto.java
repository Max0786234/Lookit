package com.searchengine.api.dto;

public record SearchResultDto(String title, String url, String snippet, double score) {
}
