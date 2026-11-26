package com.searchengine.search;

import java.util.List;

/**
 * Weak-AND dynamic pruning (Broder et al., CIKM 2003) for a disjunction of scorers.
 *
 * <p>Cursors are kept sorted by current doc. Walking them in that order, the first cursor at which
 * the running sum of {@link Scorer#maxScore()} exceeds the current top-k threshold is the
 * <em>pivot</em>: no document before the pivot's doc can enter the top k, so lagging cursors jump
 * straight to it with {@code advance}. Only documents that could beat the threshold are scored.
 *
 * <p>Exact: returns the same top k as exhaustive evaluation; the reported total is a lower bound.
 */
final class Wand {
    /** Guards against floating-point summation order making a bound fractionally too small. */
    private static final double EPSILON = 1e-9;

    private Wand() {
    }

    static TopDocs search(List<Scorer> subs, int k) {
        Scorer[] cursors = subs.toArray(Scorer[]::new);
        for (Scorer s : cursors) {
            s.nextDoc();
        }
        TopKCollector collector = new TopKCollector(k);
        boolean pruned = false;
        while (true) {
            sortByDoc(cursors);
            double threshold = collector.threshold();
            double bound = 0;
            int pivot = -1;
            for (int i = 0; i < cursors.length && cursors[i].docId() != Scorer.NO_MORE_DOCS; i++) {
                bound += cursors[i].maxScore();
                if (bound * (1 + EPSILON) > threshold) {
                    pivot = i;
                    break;
                }
            }
            if (pivot < 0) {
                // remaining documents cannot beat the threshold (or every cursor is exhausted)
                pruned |= cursors[0].docId() != Scorer.NO_MORE_DOCS;
                break;
            }
            int pivotDoc = cursors[pivot].docId();
            if (cursors[0].docId() == pivotDoc) {
                // sum in clause order, exactly like exhaustive evaluation, so ties break identically
                double score = 0;
                for (Scorer s : subs) {
                    if (s.docId() == pivotDoc) {
                        score += s.score();
                    }
                }
                collector.collect(pivotDoc, score);
                for (Scorer s : cursors) {
                    if (s.docId() == pivotDoc) {
                        s.nextDoc();
                    }
                }
            } else {
                for (int i = 0; i < pivot; i++) {
                    if (cursors[i].docId() < pivotDoc) {
                        cursors[i].advance(pivotDoc);
                        pruned = true;
                    }
                }
            }
        }
        return collector.topDocs(pruned);
    }

    /** Insertion sort: the array is small and nearly sorted between iterations. */
    private static void sortByDoc(Scorer[] a) {
        for (int i = 1; i < a.length; i++) {
            Scorer x = a[i];
            int j = i - 1;
            while (j >= 0 && a[j].docId() > x.docId()) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = x;
        }
    }
}
