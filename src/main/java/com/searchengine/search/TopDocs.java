package com.searchengine.search;

import java.util.List;

/**
 * @param totalHits number of matching documents (exact unless {@code totalHitsIsLowerBound})
 */
public record TopDocs(long totalHits, boolean totalHitsIsLowerBound, List<ScoredDoc> hits) {

    public record ScoredDoc(int docId, double score) {
    }

    public static TopDocs empty() {
        return new TopDocs(0, false, List.of());
    }
}
