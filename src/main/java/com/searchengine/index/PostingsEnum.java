package com.searchengine.index;

/**
 * Forward-only cursor over one term's postings, in increasing doc-id order.
 *
 * <p>Starts unpositioned ({@link #docId()} == -1). Doc-at-a-time scorers, conjunctions and WAND all
 * build on {@link #nextDoc()} and {@link #advance(int)}.
 */
public interface PostingsEnum {
    int NO_MORE_DOCS = Integer.MAX_VALUE;

    int docId();

    /** Moves to the next doc, returning its id or {@link #NO_MORE_DOCS}. */
    int nextDoc();

    /** Moves to the first doc ≥ {@code target}. Must only be called with target > docId(). */
    int advance(int target);

    /** Term frequency in the current doc. */
    int freq();

    /** Positions of the term in the current doc's field, ascending. */
    int[] positions();

    /** Upper bound on the number of docs (= document frequency). */
    long cost();

    PostingsEnum EMPTY = new PostingsEnum() {
        @Override
        public int docId() {
            return NO_MORE_DOCS;
        }

        @Override
        public int nextDoc() {
            return NO_MORE_DOCS;
        }

        @Override
        public int advance(int target) {
            return NO_MORE_DOCS;
        }

        @Override
        public int freq() {
            return 0;
        }

        @Override
        public int[] positions() {
            return new int[0];
        }

        @Override
        public long cost() {
            return 0;
        }
    };
}
