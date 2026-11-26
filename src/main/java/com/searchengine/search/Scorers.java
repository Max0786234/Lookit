package com.searchengine.search;

import com.searchengine.index.IndexReader;
import com.searchengine.index.PostingsEnum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Scorer implementations for the query AST. */
final class Scorers {
    private Scorers() {
    }

    static final Scorer EMPTY = new Scorer() {
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
        public double score() {
            return 0;
        }

        @Override
        public double maxScore() {
            return 0;
        }

        @Override
        public long cost() {
            return 0;
        }
    };

    /** One term in one field. */
    static final class TermScorer extends Scorer {
        private final PostingsEnum postings;
        private final Similarity.SimScorer sim;
        private final IndexReader reader;
        private final String field;

        TermScorer(PostingsEnum postings, Similarity.SimScorer sim, IndexReader reader, String field) {
            this.postings = postings;
            this.sim = sim;
            this.reader = reader;
            this.field = field;
        }

        @Override
        public int docId() {
            return postings.docId();
        }

        @Override
        public int nextDoc() {
            return postings.nextDoc();
        }

        @Override
        public int advance(int target) {
            return postings.advance(target);
        }

        @Override
        public double score() {
            return sim.score(postings.freq(), reader.fieldLength(field, postings.docId()));
        }

        @Override
        public double maxScore() {
            return sim.maxScore();
        }

        @Override
        public long cost() {
            return postings.cost();
        }
    }

    /** Union; score is the sum over sub-scorers on the current doc. */
    static final class Disjunction extends Scorer {
        private final List<Scorer> subs;
        private int doc = -1;

        Disjunction(List<Scorer> subs) {
            this.subs = subs;
        }

        @Override
        public int docId() {
            return doc;
        }

        @Override
        public int nextDoc() {
            int min = NO_MORE_DOCS;
            for (Scorer s : subs) {
                int d = s.docId();
                if (d <= doc) {
                    d = s.nextDoc();
                }
                min = Math.min(min, d);
            }
            return doc = min;
        }

        @Override
        public int advance(int target) {
            int min = NO_MORE_DOCS;
            for (Scorer s : subs) {
                int d = s.docId();
                if (d < target) {
                    d = s.advance(target);
                }
                min = Math.min(min, d);
            }
            return doc = min;
        }

        @Override
        public double score() {
            double sum = 0;
            for (Scorer s : subs) {
                if (s.docId() == doc) {
                    sum += s.score();
                }
            }
            return sum;
        }

        @Override
        public double maxScore() {
            double sum = 0;
            for (Scorer s : subs) {
                sum += s.maxScore();
            }
            return sum;
        }

        @Override
        public long cost() {
            long c = 0;
            for (Scorer s : subs) {
                c += s.cost();
            }
            return c;
        }
    }

    /** Intersection by leapfrogging from the cheapest sub-scorer. */
    static final class Conjunction extends Scorer {
        private final List<Scorer> subs;
        private int doc = -1;

        Conjunction(List<Scorer> subs) {
            List<Scorer> sorted = new ArrayList<>(subs);
            sorted.sort(Comparator.comparingLong(Scorer::cost));
            this.subs = sorted;
        }

        @Override
        public int docId() {
            return doc;
        }

        @Override
        public int nextDoc() {
            return align(subs.get(0).nextDoc());
        }

        @Override
        public int advance(int target) {
            return align(subs.get(0).advance(target));
        }

        private int align(int candidate) {
            outer:
            while (candidate != NO_MORE_DOCS) {
                for (int i = 1; i < subs.size(); i++) {
                    Scorer s = subs.get(i);
                    int d = s.docId() < candidate ? s.advance(candidate) : s.docId();
                    if (d > candidate) {
                        candidate = subs.get(0).advance(d);
                        continue outer;
                    }
                }
                return doc = candidate;
            }
            return doc = NO_MORE_DOCS;
        }

        @Override
        public double score() {
            double sum = 0;
            for (Scorer s : subs) {
                sum += s.score();
            }
            return sum;
        }

        @Override
        public double maxScore() {
            double sum = 0;
            for (Scorer s : subs) {
                sum += s.maxScore();
            }
            return sum;
        }

        @Override
        public long cost() {
            return subs.get(0).cost();
        }
    }

    /** Required scorer, minus docs matched by the excluded one. */
    static final class Exclusion extends Scorer {
        private final Scorer req;
        private final Scorer excl;

        Exclusion(Scorer req, Scorer excl) {
            this.req = req;
            this.excl = excl;
        }

        @Override
        public int docId() {
            return req.docId();
        }

        @Override
        public int nextDoc() {
            return skipExcluded(req.nextDoc());
        }

        @Override
        public int advance(int target) {
            return skipExcluded(req.advance(target));
        }

        private int skipExcluded(int d) {
            while (d != NO_MORE_DOCS) {
                int e = excl.docId() < d ? excl.advance(d) : excl.docId();
                if (e != d) {
                    return d;
                }
                d = req.nextDoc();
            }
            return d;
        }

        @Override
        public double score() {
            return req.score();
        }

        @Override
        public double maxScore() {
            return req.maxScore();
        }

        @Override
        public long cost() {
            return req.cost();
        }
    }

    /** Docs from {@code req}; {@code opt} only adds score where it also matches. */
    static final class ReqOpt extends Scorer {
        private final Scorer req;
        private final Scorer opt;

        ReqOpt(Scorer req, Scorer opt) {
            this.req = req;
            this.opt = opt;
        }

        @Override
        public int docId() {
            return req.docId();
        }

        @Override
        public int nextDoc() {
            return req.nextDoc();
        }

        @Override
        public int advance(int target) {
            return req.advance(target);
        }

        @Override
        public double score() {
            int d = req.docId();
            double s = req.score();
            int o = opt.docId() < d ? opt.advance(d) : opt.docId();
            return o == d ? s + opt.score() : s;
        }

        @Override
        public double maxScore() {
            return req.maxScore() + opt.maxScore();
        }

        @Override
        public long cost() {
            return req.cost();
        }
    }

    /** Exact phrase in one field: all terms present at their relative positions. */
    static final class PhraseScorer extends Scorer {
        private final PostingsEnum[] postings;
        private final int[] offsets;
        private final Similarity.SimScorer sim;
        private final IndexReader reader;
        private final String field;
        private int doc = -1;
        private int phraseFreq;

        PhraseScorer(PostingsEnum[] postings, int[] offsets, Similarity.SimScorer sim, IndexReader reader, String field) {
            this.postings = postings;
            this.offsets = offsets;
            this.sim = sim;
            this.reader = reader;
            this.field = field;
        }

        @Override
        public int docId() {
            return doc;
        }

        @Override
        public int nextDoc() {
            return doc == NO_MORE_DOCS ? doc : advance(doc + 1);
        }

        @Override
        public int advance(int target) {
            int candidate = postings[0].docId() < target ? postings[0].advance(target) : postings[0].docId();
            outer:
            while (candidate != NO_MORE_DOCS) {
                for (int i = 1; i < postings.length; i++) {
                    PostingsEnum p = postings[i];
                    int d = p.docId() < candidate ? p.advance(candidate) : p.docId();
                    if (d > candidate) {
                        candidate = postings[0].advance(d);
                        continue outer;
                    }
                }
                phraseFreq = countMatches();
                if (phraseFreq > 0) {
                    return doc = candidate;
                }
                candidate = postings[0].nextDoc();
            }
            return doc = NO_MORE_DOCS;
        }

        private int countMatches() {
            int matches = 0;
            int[] cursor = new int[postings.length];
            for (int start : postings[0].positions()) {
                boolean all = true;
                for (int i = 1; i < postings.length && all; i++) {
                    int want = start + offsets[i];
                    int[] pos = postings[i].positions();
                    while (cursor[i] < pos.length && pos[cursor[i]] < want) {
                        cursor[i]++;
                    }
                    all = cursor[i] < pos.length && pos[cursor[i]] == want;
                }
                if (all) {
                    matches++;
                }
            }
            return matches;
        }

        int phraseFreq() {
            return phraseFreq;
        }

        @Override
        public double score() {
            return sim.score(phraseFreq, reader.fieldLength(field, doc));
        }

        @Override
        public double maxScore() {
            return sim.maxScore();
        }

        @Override
        public long cost() {
            long min = Long.MAX_VALUE;
            for (PostingsEnum p : postings) {
                min = Math.min(min, p.cost());
            }
            return min;
        }
    }
}
