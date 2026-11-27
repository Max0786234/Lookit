package com.searchengine.vector;

import com.searchengine.search.TopDocs;
import com.searchengine.search.TopKCollector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Brute-force cosine search: exact, O(n·d) per query. Ground truth for measuring HNSW recall. */
public final class ExactVectorIndex implements VectorIndex {
    private final Map<Integer, float[]> vectors = new ConcurrentHashMap<>();

    @Override
    public void add(int docId, float[] vector) {
        vectors.put(docId, vector);
    }

    @Override
    public boolean delete(int docId) {
        return vectors.remove(docId) != null;
    }

    @Override
    public List<TopDocs.ScoredDoc> search(float[] query, int k) {
        TopKCollector top = new TopKCollector(k);
        vectors.forEach((id, v) -> top.collect(id, VectorMath.dot(query, v)));
        return top.topDocs(false).hits();
    }

    @Override
    public int size() {
        return vectors.size();
    }
}
