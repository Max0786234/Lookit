package com.searchengine.index;

import com.searchengine.document.Document;

import java.util.List;
import java.util.Optional;

/**
 * Read-side view of an index. Search, snippets, spelling, autocomplete and evaluation depend only
 * on this interface, so the storage behind it (in-memory, on-disk segments) can change freely.
 *
 * <p>Doc ids are the external document ids. Postings are ordered by doc id.
 */
public interface IndexReader {

    List<String> fields();

    /** Live (non-deleted) documents. */
    int numDocs();

    /**
     * Documents counted by collection statistics (df, total lengths). Segments keep counting
     * deleted documents until a merge drops them — like Lucene — so this can exceed numDocs.
     */
    default int maxDoc() {
        return numDocs();
    }

    Optional<Document> document(int docId);

    /** Cheap liveness check (no stored-field decoding). */
    default boolean contains(int docId) {
        return document(docId).isPresent();
    }

    /** Upper bound on the term's frequency in any document's {@code field} (for WAND bounds). */
    default int maxTermFreq(String field, String term) {
        return Integer.MAX_VALUE;
    }

    /** Lower bound on {@code field} length among documents containing the term (for WAND bounds). */
    default int minFieldLength(String field, String term) {
        return 0;
    }

    /** Ascending ids of all live documents. */
    int[] docIds();

    int docFreq(String field, String term);

    PostingsEnum postings(String field, String term);

    /** Number of indexed tokens (stopwords not counted) in {@code field} of {@code docId}. */
    int fieldLength(String field, int docId);

    long totalFieldLength(String field);

    default double avgFieldLength(String field) {
        int n = maxDoc();
        return n == 0 ? 0.0 : (double) totalFieldLength(field) / n;
    }

    /** Collection frequency: total occurrences of {@code term} in {@code field}. */
    long totalTermFreq(String field, String term);

    /** Terms of {@code field} starting with {@code prefix}, ascending, at most {@code limit}. */
    List<String> termsWithPrefix(String field, String prefix, int limit);

    int vocabularySize(String field);

    /** Increases on every change, so caches can key on it. */
    long generation();
}
