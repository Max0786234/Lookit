package com.searchengine.search;

import com.searchengine.index.IndexReader;
import com.searchengine.index.PostingsEnum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes a {@link Query} against an {@link IndexReader}: builds a scorer tree, iterates it
 * doc-at-a-time and keeps the top k.
 *
 * <p>Field-less terms are expanded into one scorer per default field, weighted by the field's
 * boost, and the per-field scores are summed.
 */
public class Searcher {
    /** Prefix queries expand to at most this many terms. */
    public static final int MAX_PREFIX_EXPANSIONS = 64;

    protected final IndexReader reader;
    protected final Similarity similarity;
    protected final Map<String, Double> fieldBoosts;

    public Searcher(IndexReader reader, Similarity similarity, Map<String, Double> fieldBoosts) {
        this.reader = reader;
        this.similarity = similarity;
        this.fieldBoosts = new LinkedHashMap<>(fieldBoosts);
    }

    public IndexReader reader() {
        return reader;
    }

    public TopDocs search(Query query, int k) {
        Scorer scorer = scorer(query);
        TopKCollector collector = new TopKCollector(k);
        for (int doc = scorer.nextDoc(); doc != Scorer.NO_MORE_DOCS; doc = scorer.nextDoc()) {
            collector.collect(doc, scorer.score());
        }
        return collector.topDocs(false);
    }

    protected Scorer scorer(Query query) {
        return switch (query) {
            case Query.Term t -> termScorer(t.field(), t.term());
            case Query.Phrase p -> phraseScorer(p);
            case Query.Prefix p -> prefixScorer(p);
            case Query.Bool b -> boolScorer(b);
        };
    }

    protected Scorer termScorer(String field, String term) {
        List<Scorer> perField = new ArrayList<>();
        for (Map.Entry<String, Double> f : fieldsFor(field).entrySet()) {
            PostingsEnum postings = reader.postings(f.getKey(), term);
            if (postings.cost() > 0) {
                Similarity.SimScorer sim = similarity.scorer(reader, f.getKey(), List.of(term), f.getValue());
                perField.add(new Scorers.TermScorer(postings, sim, reader, f.getKey()));
            }
        }
        return union(perField);
    }

    protected Scorer phraseScorer(Query.Phrase phrase) {
        if (phrase.terms().size() == 1) {
            return termScorer(phrase.field(), phrase.terms().get(0));
        }
        int[] offsets = new int[phrase.terms().size()];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = phrase.positions().get(i) - phrase.positions().get(0);
        }
        List<Scorer> perField = new ArrayList<>();
        outer:
        for (Map.Entry<String, Double> f : fieldsFor(phrase.field()).entrySet()) {
            PostingsEnum[] postings = new PostingsEnum[offsets.length];
            for (int i = 0; i < offsets.length; i++) {
                postings[i] = reader.postings(f.getKey(), phrase.terms().get(i));
                if (postings[i].cost() == 0) {
                    continue outer;
                }
            }
            Similarity.SimScorer sim = similarity.scorer(reader, f.getKey(), phrase.terms(), f.getValue());
            perField.add(new Scorers.PhraseScorer(postings, offsets, sim, reader, f.getKey()));
        }
        return union(perField);
    }

    private Scorer prefixScorer(Query.Prefix prefix) {
        List<Scorer> subs = new ArrayList<>();
        for (String field : fieldsFor(prefix.field()).keySet()) {
            for (String term : reader.termsWithPrefix(field, prefix.prefix(), MAX_PREFIX_EXPANSIONS)) {
                subs.add(termScorer(field, term));
            }
        }
        return union(subs);
    }

    private Scorer boolScorer(Query.Bool bool) {
        List<Scorer> must = new ArrayList<>();
        List<Scorer> should = new ArrayList<>();
        List<Scorer> mustNot = new ArrayList<>();
        for (Query.Clause c : bool.clauses()) {
            Scorer s = scorer(c.query());
            switch (c.occur()) {
                case MUST -> must.add(s);
                case SHOULD -> should.add(s);
                case MUST_NOT -> mustNot.add(s);
            }
        }
        Scorer base;
        if (!must.isEmpty()) {
            if (must.stream().anyMatch(s -> s == Scorers.EMPTY)) {
                return Scorers.EMPTY;
            }
            base = must.size() == 1 ? must.get(0) : new Scorers.Conjunction(must);
            Scorer opt = union(should);
            if (opt != Scorers.EMPTY) {
                base = new Scorers.ReqOpt(base, opt);
            }
        } else {
            base = union(should);
        }
        Scorer excluded = union(mustNot);
        return base == Scorers.EMPTY || excluded == Scorers.EMPTY ? base : new Scorers.Exclusion(base, excluded);
    }

    protected Map<String, Double> fieldsFor(String field) {
        if (field == null) {
            return fieldBoosts;
        }
        if (!reader.fields().contains(field)) {
            return Map.of();
        }
        return Map.of(field, fieldBoosts.getOrDefault(field, 1.0));
    }

    protected static Scorer union(List<Scorer> scorers) {
        List<Scorer> live = scorers.stream().filter(s -> s != Scorers.EMPTY).toList();
        if (live.isEmpty()) {
            return Scorers.EMPTY;
        }
        return live.size() == 1 ? live.get(0) : new Scorers.Disjunction(live);
    }
}
