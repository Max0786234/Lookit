package com.searchengine.search;

import com.searchengine.index.IndexReader;

import java.util.List;

/** Turns (term statistics, term frequency, field length) into a relevance score. */
public interface Similarity {

    /**
     * @param terms one term for a term query; all terms of a phrase (IDFs are summed, as in Lucene)
     */
    SimScorer scorer(IndexReader reader, String field, List<String> terms, double boost);

    interface SimScorer {
        double score(int freq, int fieldLength);

        /** Upper bound of {@link #score} over all freq / length — used by WAND. */
        double maxScore();
    }
}
