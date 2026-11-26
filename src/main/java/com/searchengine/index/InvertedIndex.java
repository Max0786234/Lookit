package com.searchengine.index;

import com.searchengine.document.Document;
import com.searchengine.processor.TextProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory positional inverted index.
 *
 * <p>Thread-safety: many concurrent readers, one writer (a crawl can add documents while searches run).
 * Collection getters return snapshots so callers never iterate a map that is being mutated.
 *
 * <p>{@link #generation()} increases on every mutation; caches key on it so they can never serve
 * results computed against an older version of the index.
 */
public class InvertedIndex {
    private final TextProcessor textProcessor;
    private final Map<Integer, Document> documents = new HashMap<>();
    private final Map<Integer, Integer> documentLengths = new HashMap<>();
    private final Map<String, Map<Integer, Posting>> index = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private long totalLength;
    private volatile long generation;

    public InvertedIndex(TextProcessor textProcessor) {
        this.textProcessor = textProcessor;
    }

    public void addDocument(Document document) {
        List<String> tokens = new ArrayList<>();
        tokens.addAll(textProcessor.tokenize(document.title()));
        tokens.addAll(textProcessor.tokenize(document.content()));

        lock.writeLock().lock();
        try {
            if (documents.containsKey(document.id())) {
                removeLocked(document.id());
            }
            documents.put(document.id(), document);
            documentLengths.put(document.id(), tokens.size());
            totalLength += tokens.size();

            for (int position = 0; position < tokens.size(); position++) {
                String token = tokens.get(position);
                Map<Integer, Posting> postingList = index.computeIfAbsent(token, ignored -> new HashMap<>());
                Posting posting = postingList.computeIfAbsent(document.id(), ignored -> new Posting(document.id()));
                posting.addPosition(position);
            }
            generation++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeDocument(int documentId) {
        lock.writeLock().lock();
        try {
            boolean removed = removeLocked(documentId);
            if (removed) {
                generation++;
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean removeLocked(int documentId) {
        if (documents.remove(documentId) == null) {
            return false;
        }
        Integer length = documentLengths.remove(documentId);
        totalLength -= length == null ? 0 : length;
        index.values().removeIf(postings -> postings.remove(documentId) != null && postings.isEmpty());
        return true;
    }

    public Collection<Posting> getPostings(String term) {
        return getPostingList(term).values();
    }

    public Map<Integer, Posting> getPostingList(String term) {
        lock.readLock().lock();
        try {
            return Map.copyOf(index.getOrDefault(term, Map.of()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Document> getDocument(int documentId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(documents.get(documentId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public int documentCount() {
        lock.readLock().lock();
        try {
            return documents.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int vocabularySize() {
        lock.readLock().lock();
        try {
            return index.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int documentFrequency(String term) {
        lock.readLock().lock();
        try {
            return index.getOrDefault(term, Map.of()).size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int documentLength(int documentId) {
        lock.readLock().lock();
        try {
            return documentLengths.getOrDefault(documentId, 0);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** O(1): maintained as a running total rather than recomputed per scored posting. */
    public double averageDocumentLength() {
        lock.readLock().lock();
        try {
            return documents.isEmpty() ? 0.0 : (double) totalLength / documents.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public long totalLength() {
        lock.readLock().lock();
        try {
            return totalLength;
        } finally {
            lock.readLock().unlock();
        }
    }

    public long generation() {
        return generation;
    }

    public List<Document> getAllDocuments() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(documents.values());
        } finally {
            lock.readLock().unlock();
        }
    }
}
