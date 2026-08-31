package com.searchengine.ranking;

import com.searchengine.index.InvertedIndex;
import com.searchengine.index.Posting;

public interface Ranker {
    double score(String term, Posting posting, InvertedIndex index);
}
