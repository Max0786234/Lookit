package com.searchengine.query;

import com.searchengine.document.Document;

public record SearchResult(Document document, double score) {
}
