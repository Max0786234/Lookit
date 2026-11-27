package com.searchengine.vector;

import com.searchengine.search.TopDocs;

import java.util.List;

/** Nearest-neighbour index over unit vectors; scores are cosine similarities. */
public interface VectorIndex {

    /** Adds or replaces the vector for {@code docId}. */
    void add(int docId, float[] vector);

    boolean delete(int docId);

    List<TopDocs.ScoredDoc> search(float[] query, int k);

    int size();
}
