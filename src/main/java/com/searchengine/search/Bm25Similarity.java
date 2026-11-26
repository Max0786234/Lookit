package com.searchengine.search;

import com.searchengine.index.IndexReader;

import java.util.List;

/**
 * Okapi BM25 with Lucene's non-negative IDF: {@code ln(1 + (N - df + 0.5) / (df + 0.5))}.
 * Defaults k1 = 1.2, b = 0.75 (Lucene / Robertson defaults).
 */
public final class Bm25Similarity implements Similarity {
    private final double k1;
    private final double b;

    public Bm25Similarity() {
        this(1.2, 0.75);
    }

    public Bm25Similarity(double k1, double b) {
        this.k1 = k1;
        this.b = b;
    }

    public static double idf(long numDocs, long docFreq) {
        return Math.log(1.0 + (numDocs - docFreq + 0.5) / (docFreq + 0.5));
    }

    @Override
    public SimScorer scorer(IndexReader reader, String field, List<String> terms, double boost) {
        int n = reader.numDocs();
        double idf = 0;
        for (String t : terms) {
            idf += idf(n, reader.docFreq(field, t));
        }
        double avgLength = Math.max(reader.avgFieldLength(field), 1e-9);
        double weight = boost * idf;
        return new SimScorer() {
            @Override
            public double score(int freq, int fieldLength) {
                double norm = k1 * (1 - b + b * fieldLength / avgLength);
                return weight * freq * (k1 + 1) / (freq + norm);
            }

            @Override
            public double maxScore() {
                return weight * (k1 + 1);
            }
        };
    }

    @Override
    public String toString() {
        return "BM25(k1=" + k1 + ", b=" + b + ")";
    }
}
