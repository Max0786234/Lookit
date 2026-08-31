package com.searchengine.api.dto;

import java.util.List;

public record SearchResponseDto(String query, List<SearchResultDto> results) {
}
