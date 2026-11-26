package com.searchengine.search;

import com.searchengine.index.IndexReader;
import com.searchengine.index.PostingsEnum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BM25F (Robertson, Zaragoza &amp; Taylor 2004; "simple BM25F" of Pérez-Agüera et al. 2010).
 *
 * <p>For a field-less term the per-field frequencies are length-normalised and weighted
 * <em>before</em> saturation:
 * <pre>
 *   tf~ = Σ_f  w_f · tf_f / (1 − b_f + b_f · len_f / avglen_f)
 *   score = idf · tf~ · (k1 + 1) / (k1 + tf~)
 * </pre>
 * so a term that occurs in both title and body is not saturated twice (the flaw of summing
 * per-field BM25). Document frequency is taken as the maximum over fields, as Lucene's
 * CombinedFieldQuery does. Field-restricted queries fall back to plain per-field BM25.
 */
public final class Bm25fSearcher extends Searcher {

    public record FieldParams(double weight, double b) {
    }

    private final double k1;
    private final Map<String, FieldParams> params;

    public Bm25fSearcher(IndexReader reader, double k1, Map<String, FieldParams> params) {
        super(reader, new Bm25Similarity(k1, 0.75), weights(params));
        this.k1 = k1;
        this.params = new LinkedHashMap<>(params);
    }

    private static Map<String, Double> weights(Map<String, FieldParams> params) {
        Map<String, Double> w = new LinkedHashMap<>();
        params.forEach((f, p) -> w.put(f, p.weight()));
        return w;
    }

    @Override
    protected Scorer termScorer(String field, String term) {
        if (field != null) {
            return super.termScorer(field, term);
        }
        List<Source> sources = new ArrayList<>();
        int maxDf = 0;
        for (Map.Entry<String, FieldParams> e : params.entrySet()) {
            PostingsEnum p = reader.postings(e.getKey(), term);
            if (p.cost() > 0) {
                sources.add(new Source(new PostingsFreq(p), e.getKey(), e.getValue()));
                maxDf = Math.max(maxDf, reader.docFreq(e.getKey(), term));
            }
        }
        if (sources.isEmpty()) {
            return Scorers.EMPTY;
        }
        return new Bm25fScorer(sources, Bm25Similarity.idf(reader.numDocs(), maxDf));
    }

    @Override
    protected Scorer phraseScorer(Query.Phrase phrase) {
        if (phrase.field() != null || phrase.terms().size() == 1) {
            return super.phraseScorer(phrase);
        }
        List<Source> sources = new ArrayList<>();
        for (Map.Entry<String, FieldParams> e : params.entrySet()) {
            Scorer s = super.phraseScorer(new Query.Phrase(e.getKey(), phrase.terms(), phrase.positions()));
            if (s instanceof Scorers.PhraseScorer ps) {
                sources.add(new Source(new PhraseFreq(ps), e.getKey(), e.getValue()));
            }
        }
        if (sources.isEmpty()) {
            return Scorers.EMPTY;
        }
        double idf = 0;
        for (String t : phrase.terms()) {
            int maxDf = 0;
            for (String f : params.keySet()) {
                maxDf = Math.max(maxDf, reader.docFreq(f, t));
            }
            idf += Bm25Similarity.idf(reader.numDocs(), maxDf);
        }
        return new Bm25fScorer(sources, idf);
    }

    /** A per-field frequency cursor. */
    private interface FreqIterator {
        int docId();

        int nextDoc();

        int advance(int target);

        int freq();

        long cost();
    }

    private record PostingsFreq(PostingsEnum p) implements FreqIterator {
        public int docId() {
            return p.docId();
        }

        public int nextDoc() {
            return p.nextDoc();
        }

        public int advance(int target) {
            return p.advance(target);
        }

        public int freq() {
            return p.freq();
        }

        public long cost() {
            return p.cost();
        }
    }

    private record PhraseFreq(Scorers.PhraseScorer s) implements FreqIterator {
        public int docId() {
            return s.docId();
        }

        public int nextDoc() {
            return s.nextDoc();
        }

        public int advance(int target) {
            return s.advance(target);
        }

        public int freq() {
            return s.phraseFreq();
        }

        public long cost() {
            return s.cost();
        }
    }

    private record Source(FreqIterator it, String field, FieldParams params) {
    }

    private final class Bm25fScorer extends Scorer {
        private final Source[] sources;
        private final double[] avgLength;
        private final double idf;
        private int doc = -1;

        Bm25fScorer(List<Source> sources, double idf) {
            this.sources = sources.toArray(Source[]::new);
            this.avgLength = new double[this.sources.length];
            for (int i = 0; i < this.sources.length; i++) {
                avgLength[i] = Math.max(reader.avgFieldLength(this.sources[i].field()), 1e-9);
            }
            this.idf = idf;
        }

        @Override
        public int docId() {
            return doc;
        }

        @Override
        public int nextDoc() {
            int min = NO_MORE_DOCS;
            for (Source s : sources) {
                int d = s.it().docId();
                if (d <= doc) {
                    d = s.it().nextDoc();
                }
                min = Math.min(min, d);
            }
            return doc = min;
        }

        @Override
        public int advance(int target) {
            int min = NO_MORE_DOCS;
            for (Source s : sources) {
                int d = s.it().docId();
                if (d < target) {
                    d = s.it().advance(target);
                }
                min = Math.min(min, d);
            }
            return doc = min;
        }

        @Override
        public double score() {
            double tf = 0;
            for (int i = 0; i < sources.length; i++) {
                Source s = sources[i];
                if (s.it().docId() == doc) {
                    double b = s.params().b();
                    double norm = 1 - b + b * reader.fieldLength(s.field(), doc) / avgLength[i];
                    tf += s.params().weight() * s.it().freq() / norm;
                }
            }
            return idf * tf * (k1 + 1) / (k1 + tf);
        }

        @Override
        public double maxScore() {
            return idf * (k1 + 1);
        }

        @Override
        public long cost() {
            long c = 0;
            for (Source s : sources) {
                c += s.it().cost();
            }
            return c;
        }
    }
}
