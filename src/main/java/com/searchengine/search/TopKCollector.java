package com.searchengine.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Keeps the k best (score desc, doc id asc) hits in a min-heap: O(n log k) instead of sorting all n.
 * {@link #threshold()} exposes the current k-th score for dynamic pruning (WAND).
 */
public final class TopKCollector {
    // Worst hit at the head: lowest score, and for equal scores the highest doc id.
    private static final Comparator<TopDocs.ScoredDoc> WORST_FIRST =
            Comparator.comparingDouble(TopDocs.ScoredDoc::score)
                    .thenComparing(TopDocs.ScoredDoc::docId, Comparator.reverseOrder());

    private final int k;
    private final PriorityQueue<TopDocs.ScoredDoc> heap;
    private long totalHits;

    public TopKCollector(int k) {
        this.k = Math.max(k, 1);
        this.heap = new PriorityQueue<>(this.k + 1, WORST_FIRST);
    }

    public void collect(int docId, double score) {
        totalHits++;
        if (heap.size() < k) {
            heap.add(new TopDocs.ScoredDoc(docId, score));
        } else if (WORST_FIRST.compare(new TopDocs.ScoredDoc(docId, score), heap.peek()) > 0) {
            heap.poll();
            heap.add(new TopDocs.ScoredDoc(docId, score));
        }
    }

    /** Score a new doc must beat to enter the top k; -∞ until the heap is full. */
    public double threshold() {
        return heap.size() < k ? Double.NEGATIVE_INFINITY : heap.peek().score();
    }

    public TopDocs topDocs(boolean totalIsLowerBound) {
        List<TopDocs.ScoredDoc> hits = new ArrayList<>(heap);
        hits.sort(WORST_FIRST.reversed());
        return new TopDocs(totalHits, totalIsLowerBound, hits);
    }
}
