package com.searchengine.search;

import com.searchengine.index.IndexReader;

import java.util.List;

/**
 * Classic log-TF × IDF with cosine-style length normalisation {@code 1/sqrt(len)}.
 * Kept as an evaluation baseline to show what BM25's saturation and pivoted length norm buy.
 */
public final class TfIdfSimilarity implements Similarity {

    @Override
    public SimScorer scorer(IndexReader reader, String field, List<String> terms, double boost) {
        int n = reader.maxDoc();
        double idf = 0;
        for (String t : terms) {
            int df = reader.docFreq(field, t);
            idf += df == 0 ? 0 : Math.log((double) n / df) + 1.0;
        }
        double weight = boost * idf;
        return new SimScorer() {
            @Override
            public double score(int freq, int fieldLength) {
                return weight * (1 + Math.log(freq)) / Math.sqrt(Math.max(fieldLength, 1));
            }

            @Override
            public double maxScore() {
                return Double.POSITIVE_INFINITY;
            }
        };
    }

    @Override
    public String toString() {
        return "TF-IDF";
    }
}
