package com.searchengine.index;

import com.searchengine.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * One logical index over several sub-readers (segments plus the RAM buffer) whose <em>live</em>
 * documents are disjoint. Postings are k-way merged by doc id; statistics are summed.
 */
public final class MultiReader implements IndexReader {
    private final List<IndexReader> subs;
    private final long generation;

    public MultiReader(List<? extends IndexReader> subs, long generation) {
        this.subs = List.copyOf(subs);
        this.generation = generation;
    }

    public List<IndexReader> subReaders() {
        return subs;
    }

    @Override
    public List<String> fields() {
        return subs.isEmpty() ? Fields.ALL : subs.get(0).fields();
    }

    @Override
    public int numDocs() {
        return subs.stream().mapToInt(IndexReader::numDocs).sum();
    }

    @Override
    public int maxDoc() {
        return subs.stream().mapToInt(IndexReader::maxDoc).sum();
    }

    @Override
    public boolean contains(int docId) {
        return owner(docId) != null;
    }

    @Override
    public Optional<Document> document(int docId) {
        IndexReader r = owner(docId);
        return r == null ? Optional.empty() : r.document(docId);
    }

    /** Newest first, so a replaced document is found in the reader that holds its live copy. */
    private IndexReader owner(int docId) {
        for (int i = subs.size() - 1; i >= 0; i--) {
            if (subs.get(i).contains(docId)) {
                return subs.get(i);
            }
        }
        return null;
    }

    @Override
    public int[] docIds() {
        return subs.stream().flatMapToInt(r -> IntStream.of(r.docIds())).sorted().toArray();
    }

    @Override
    public int docFreq(String field, String term) {
        return subs.stream().mapToInt(r -> r.docFreq(field, term)).sum();
    }

    @Override
    public long totalTermFreq(String field, String term) {
        return subs.stream().mapToLong(r -> r.totalTermFreq(field, term)).sum();
    }

    @Override
    public int maxTermFreq(String field, String term) {
        return subs.stream().mapToInt(r -> r.maxTermFreq(field, term)).max().orElse(0);
    }

    @Override
    public int minFieldLength(String field, String term) {
        int min = Integer.MAX_VALUE;
        for (IndexReader r : subs) {
            if (r.docFreq(field, term) > 0) {
                min = Math.min(min, r.minFieldLength(field, term));
            }
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    @Override
    public PostingsEnum postings(String field, String term) {
        List<PostingsEnum> live = new ArrayList<>();
        for (IndexReader r : subs) {
            PostingsEnum p = r.postings(field, term);
            if (p.cost() > 0) {
                live.add(p);
            }
        }
        if (live.isEmpty()) {
            return PostingsEnum.EMPTY;
        }
        return live.size() == 1 ? live.get(0) : new MergedPostings(live.toArray(PostingsEnum[]::new));
    }

    @Override
    public int fieldLength(String field, int docId) {
        IndexReader r = owner(docId);
        return r == null ? 0 : r.fieldLength(field, docId);
    }

    @Override
    public long totalFieldLength(String field) {
        return subs.stream().mapToLong(r -> r.totalFieldLength(field)).sum();
    }

    @Override
    public List<String> termsWithPrefix(String field, String prefix, int limit) {
        TreeSet<String> merged = new TreeSet<>();
        for (IndexReader r : subs) {
            merged.addAll(r.termsWithPrefix(field, prefix, limit));
        }
        List<String> out = new ArrayList<>(limit == Integer.MAX_VALUE ? merged.size() : Math.min(limit, merged.size()));
        for (String t : merged) {
            if (out.size() >= limit) {
                break;
            }
            out.add(t);
        }
        return out;
    }

    @Override
    public int vocabularySize(String field) {
        if (subs.size() == 1) {
            return subs.get(0).vocabularySize(field);
        }
        return termsWithPrefix(field, "", Integer.MAX_VALUE).size();
    }

    @Override
    public long generation() {
        return generation;
    }

    /** k-way union of doc-disjoint postings lists. */
    static final class MergedPostings implements PostingsEnum {
        private final PostingsEnum[] subs;
        private int doc = -1;
        private PostingsEnum current;

        MergedPostings(PostingsEnum[] subs) {
            this.subs = subs;
        }

        @Override
        public int docId() {
            return doc;
        }

        @Override
        public int nextDoc() {
            return select(true, doc + 1);
        }

        @Override
        public int advance(int target) {
            return select(false, target);
        }

        private int select(boolean next, int target) {
            int min = NO_MORE_DOCS;
            PostingsEnum best = null;
            for (PostingsEnum p : subs) {
                int d = p.docId();
                if (d < target) {
                    d = next && d == doc ? p.nextDoc() : p.advance(target);
                }
                if (d < min) {
                    min = d;
                    best = p;
                }
            }
            current = best;
            return doc = min;
        }

        @Override
        public int freq() {
            return current.freq();
        }

        @Override
        public int[] positions() {
            return current.positions();
        }

        @Override
        public long cost() {
            long c = 0;
            for (PostingsEnum p : subs) {
                c += p.cost();
            }
            return c;
        }
    }
}
