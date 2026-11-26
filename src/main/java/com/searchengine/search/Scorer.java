package com.searchengine.search;

import com.searchengine.index.PostingsEnum;

/**
 * Doc-at-a-time iterator over matching documents with a score for the current one.
 * Same cursor contract as {@link PostingsEnum}: starts at -1, ends at {@link PostingsEnum#NO_MORE_DOCS}.
 */
public abstract class Scorer {
    public static final int NO_MORE_DOCS = PostingsEnum.NO_MORE_DOCS;

    public abstract int docId();

    public abstract int nextDoc();

    /** First doc ≥ target; target must be greater than {@link #docId()}. */
    public abstract int advance(int target);

    public abstract double score();

    /** Upper bound on {@link #score()} for any document. */
    public abstract double maxScore();

    /** Estimated number of matches (used to order conjunctions cheapest-first). */
    public abstract long cost();
}
