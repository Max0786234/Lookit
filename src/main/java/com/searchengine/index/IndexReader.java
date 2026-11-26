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

    Optional<Document> document(int docId);

    /** Ascending ids of all live documents. */
    int[] docIds();

    int docFreq(String field, String term);

    PostingsEnum postings(String field, String term);

    /** Number of indexed tokens (stopwords not counted) in {@code field} of {@code docId}. */
    int fieldLength(String field, int docId);

    long totalFieldLength(String field);

    default double avgFieldLength(String field) {
        int n = numDocs();
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
