package com.searchengine.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
        @NotBlank @Size(max = 512) String title,
        @NotBlank @Size(max = 2048) String url,
        @NotBlank @Size(max = 1_000_000) String content) {
}
