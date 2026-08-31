package com.searchengine.ranking;

import com.searchengine.index.InvertedIndex;
import com.searchengine.index.Posting;

public class TfIdfRanker implements Ranker {
    @Override
    public double score(String term, Posting posting, InvertedIndex index) {
        double tf = 1.0 + Math.log(posting.frequency());
        double idf = Math.log((double) index.documentCount() / index.documentFrequency(term));
        return tf * idf;
    }
}
