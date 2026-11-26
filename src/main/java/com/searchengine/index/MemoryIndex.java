package com.searchengine.index;

import com.searchengine.analysis.Analyzer;
import com.searchengine.analysis.Token;
import com.searchengine.document.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Fielded, positional, in-memory index.
 *
 * <p>Per field: a sorted term dictionary (enables prefix expansion and autocomplete) mapping to
 * doc-ordered postings arrays. Per document we remember its distinct terms so a delete touches
 * only that document's postings, not the whole vocabulary.
 *
 * <p>Concurrency: read/write lock; {@link #postings} copies the doc array under the read lock so a
 * cursor stays valid while writers continue.
 */
public final class MemoryIndex implements MutableIndex {

    private final Analyzer analyzer;
    private final List<String> fields;
    private final TreeMap<Integer, Document> documents = new TreeMap<>();
    private final Map<String, FieldData> fieldData = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile long generation;

    public MemoryIndex(Analyzer analyzer) {
        this(analyzer, Fields.ALL);
    }

    public MemoryIndex(Analyzer analyzer, List<String> fields) {
        this.analyzer = analyzer;
        this.fields = List.copyOf(fields);
        for (String f : fields) {
            fieldData.put(f, new FieldData());
        }
    }

    @Override
    public Analyzer analyzer() {
        return analyzer;
    }

    @Override
    public void add(Document document) {
        // Analyze outside the lock: it is the expensive part and touches no shared state.
        Map<String, Map<String, int[]>> analyzed = new HashMap<>();
        Map<String, Integer> lengths = new HashMap<>();
        for (String field : fields) {
            List<Token> tokens = analyzer.analyze(Fields.text(document, field));
            lengths.put(field, tokens.size());
            Map<String, List<Integer>> byTerm = new LinkedHashMap<>();
            for (Token t : tokens) {
                byTerm.computeIfAbsent(t.term(), k -> new ArrayList<>()).add(t.position());
            }
            Map<String, int[]> positions = new LinkedHashMap<>();
            byTerm.forEach((term, list) -> positions.put(term, list.stream().mapToInt(Integer::intValue).toArray()));
            analyzed.put(field, positions);
        }

        lock.writeLock().lock();
        try {
            deleteLocked(document.id());
            documents.put(document.id(), document);
            for (String field : fields) {
                FieldData fd = fieldData.get(field);
                Map<String, int[]> positions = analyzed.get(field);
                fd.lengths.put(document.id(), lengths.get(field));
                fd.totalLength += lengths.get(field);
                fd.docTerms.put(document.id(), positions.keySet().toArray(String[]::new));
                int length = lengths.get(field);
                positions.forEach((term, pos) -> fd.terms.computeIfAbsent(term, k -> new TermPostings())
                        .put(document.id(), pos, length));
            }
            generation++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean delete(int docId) {
        lock.writeLock().lock();
        try {
            boolean removed = deleteLocked(docId);
            if (removed) {
                generation++;
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean deleteLocked(int docId) {
        if (documents.remove(docId) == null) {
            return false;
        }
        for (FieldData fd : fieldData.values()) {
            Integer len = fd.lengths.remove(docId);
            fd.totalLength -= len == null ? 0 : len;
            String[] terms = fd.docTerms.remove(docId);
            if (terms == null) {
                continue;
            }
            for (String term : terms) {
                TermPostings tp = fd.terms.get(term);
                if (tp != null && tp.remove(docId) && tp.size == 0) {
                    fd.terms.remove(term);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> fields() {
        return fields;
    }

    @Override
    public int numDocs() {
        lock.readLock().lock();
        try {
            return documents.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Document> document(int docId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(documents.get(docId));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int[] docIds() {
        lock.readLock().lock();
        try {
            return documents.keySet().stream().mapToInt(Integer::intValue).toArray();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean contains(int docId) {
        lock.readLock().lock();
        try {
            return documents.containsKey(docId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int maxTermFreq(String field, String term) {
        lock.readLock().lock();
        try {
            TermPostings tp = field(field).terms.get(term);
            return tp == null ? 0 : tp.maxFreq;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int minFieldLength(String field, String term) {
        lock.readLock().lock();
        try {
            TermPostings tp = field(field).terms.get(term);
            return tp == null ? 0 : tp.minLength;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int docFreq(String field, String term) {
        lock.readLock().lock();
        try {
            TermPostings tp = field(field).terms.get(term);
            return tp == null ? 0 : tp.size;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long totalTermFreq(String field, String term) {
        lock.readLock().lock();
        try {
            TermPostings tp = field(field).terms.get(term);
            return tp == null ? 0 : tp.totalFreq;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public PostingsEnum postings(String field, String term) {
        lock.readLock().lock();
        try {
            TermPostings tp = field(field).terms.get(term);
            if (tp == null) {
                return PostingsEnum.EMPTY;
            }
            return new ArrayPostingsEnum(Arrays.copyOf(tp.docs, tp.size), Arrays.copyOf(tp.positions, tp.size), tp.size);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int fieldLength(String field, int docId) {
        lock.readLock().lock();
        try {
            return field(field).lengths.getOrDefault(docId, 0);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long totalFieldLength(String field) {
        lock.readLock().lock();
        try {
            return field(field).totalLength;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<String> termsWithPrefix(String field, String prefix, int limit) {
        lock.readLock().lock();
        try {
            List<String> out = new ArrayList<>();
            NavigableMap<String, TermPostings> tail = field(field).terms.tailMap(prefix, true);
            for (String term : tail.keySet()) {
                if (!term.startsWith(prefix) || out.size() >= limit) {
                    break;
                }
                out.add(term);
            }
            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int vocabularySize(String field) {
        lock.readLock().lock();
        try {
            return field(field).terms.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long generation() {
        return generation;
    }

    private FieldData field(String field) {
        FieldData fd = fieldData.get(field);
        if (fd == null) {
            throw new IllegalArgumentException("Unknown field: " + field);
        }
        return fd;
    }

    private static final class FieldData {
        final TreeMap<String, TermPostings> terms = new TreeMap<>();
        final Map<Integer, Integer> lengths = new HashMap<>();
        final Map<Integer, String[]> docTerms = new HashMap<>();
        long totalLength;
    }

    /** Doc-sorted growable postings for one term. */
    private static final class TermPostings {
        int[] docs = new int[2];
        int[][] positions = new int[2][];
        int size;
        long totalFreq;
        // bounds only ever widen (deletes do not shrink them), so they stay valid upper/lower bounds
        int maxFreq;
        int minLength = Integer.MAX_VALUE;

        void put(int doc, int[] pos, int fieldLength) {
            maxFreq = Math.max(maxFreq, pos.length);
            minLength = Math.min(minLength, fieldLength);
            if (size == docs.length) {
                docs = Arrays.copyOf(docs, size * 2);
                positions = Arrays.copyOf(positions, size * 2);
            }
            int at = size == 0 || docs[size - 1] < doc ? size : insertionPoint(doc);
            if (at < size) {
                System.arraycopy(docs, at, docs, at + 1, size - at);
                System.arraycopy(positions, at, positions, at + 1, size - at);
            }
            docs[at] = doc;
            positions[at] = pos;
            size++;
            totalFreq += pos.length;
        }

        boolean remove(int doc) {
            int at = Arrays.binarySearch(docs, 0, size, doc);
            if (at < 0) {
                return false;
            }
            totalFreq -= positions[at].length;
            System.arraycopy(docs, at + 1, docs, at, size - at - 1);
            System.arraycopy(positions, at + 1, positions, at, size - at - 1);
            size--;
            positions[size] = null;
            return true;
        }

        private int insertionPoint(int doc) {
            int found = Arrays.binarySearch(docs, 0, size, doc);
            return found >= 0 ? found : -found - 1;
        }
    }
}
