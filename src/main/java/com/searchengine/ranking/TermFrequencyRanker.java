package com.searchengine.ranking;

import com.searchengine.index.InvertedIndex;
import com.searchengine.index.Posting;

public class TermFrequencyRanker implements Ranker {
    @Override
    public double score(String term, Posting posting, InvertedIndex index) {
        return posting.frequency();
    }
}
