package com.searchengine.api.dto;

import java.util.List;

public record SuggestResponseDto(String query, List<String> suggestions) {
}
