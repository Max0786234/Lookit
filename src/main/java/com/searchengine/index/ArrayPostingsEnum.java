package com.searchengine.index;

import java.util.Arrays;

/** {@link PostingsEnum} over parallel arrays; {@code advance} gallops then binary-searches. */
public final class ArrayPostingsEnum implements PostingsEnum {
    private final int[] docs;
    private final int[][] positions;
    private final int size;
    private int index = -1;

    public ArrayPostingsEnum(int[] docs, int[][] positions, int size) {
        this.docs = docs;
        this.positions = positions;
        this.size = size;
    }

    @Override
    public int docId() {
        if (index < 0) {
            return -1;
        }
        return index < size ? docs[index] : NO_MORE_DOCS;
    }

    @Override
    public int nextDoc() {
        index++;
        return docId();
    }

    @Override
    public int advance(int target) {
        int lo = Math.max(index + 1, 0);
        if (lo >= size) {
            index = size;
            return NO_MORE_DOCS;
        }
        int step = 1;
        int hi = lo;
        while (hi < size && docs[hi] < target) {
            lo = hi + 1;
            hi += step;
            step <<= 1;
        }
        hi = Math.min(hi, size - 1);
        if (lo > hi) {
            index = size;
            return NO_MORE_DOCS;
        }
        int found = Arrays.binarySearch(docs, lo, hi + 1, target);
        index = found >= 0 ? found : -found - 1;
        return docId();
    }

    @Override
    public int freq() {
        return positions[index].length;
    }

    @Override
    public int[] positions() {
        return positions[index];
    }

    @Override
    public long cost() {
        return size;
    }
}
