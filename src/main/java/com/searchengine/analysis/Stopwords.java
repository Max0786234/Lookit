package com.searchengine.analysis;

import java.util.Set;

/** Stopword lists. */
public final class Stopwords {
    /**
     * The classic 33-word English list (as used by Lucene's StandardAnalyzer). Deliberately short:
     * aggressive lists hurt phrase queries and BM25 already discounts frequent terms via IDF.
     */
    public static final Set<String> ENGLISH = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is", "it",
            "no", "not", "of", "on", "or", "such", "that", "the", "their", "then", "there", "these",
            "they", "this", "to", "was", "will", "with");

    private Stopwords() {
    }
}
