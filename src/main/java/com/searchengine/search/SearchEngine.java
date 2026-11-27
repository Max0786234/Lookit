package com.searchengine.search;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;
import com.searchengine.index.Fields;
import com.searchengine.index.MemoryIndex;
import com.searchengine.index.MutableIndex;
import com.searchengine.vector.Encoder;
import com.searchengine.vector.VectorIndex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Spring-free engine facade: index + query parsing + ranking + snippets + result cache, plus the
 * optional dense (semantic) retriever, hybrid fusion and query suggestions.
 * The REST layer, the evaluation harness and benchmarks all use this class.
 */
public final class SearchEngine {
    /** Deepest result rank that can be paged to. */
    public static final int MAX_RESULT_WINDOW = 1000;
    static final int SNIPPET_WORDS = 30;

    private final MutableIndex index;
    private final Searcher searcher;
    private final SnippetGenerator snippets;
    private final QueryParser parser;
    private final QueryCache<TopDocs> cache = new QueryCache<>(512);

    public SearchEngine(MutableIndex index, Searcher searcher) {
        this.index = index;
        this.searcher = searcher;
        Analyzer analyzer = index.analyzer();
        this.snippets = new SnippetGenerator(analyzer::normalize, SNIPPET_WORDS);
        this.parser = new QueryParser(analyzer, java.util.Set.copyOf(index.fields()));
    }

    /** BM25F k1 tuned on SciFact train (docs/evaluation.md). */
    public static final double BM25F_K1 = 0.9;

    /** English analyzer, in-memory index, BM25F over title and body with tuned parameters. */
    public static SearchEngine inMemory() {
        MemoryIndex index = new MemoryIndex(Analyzer.english());
        return new SearchEngine(index, new Bm25fSearcher(index, BM25F_K1, defaultBm25fParams()));
    }

    /** Field weight and length normalisation tuned on SciFact train: title counts double. */
    public static Map<String, Bm25fSearcher.FieldParams> defaultBm25fParams() {
        return Map.of(Fields.TITLE, new Bm25fSearcher.FieldParams(2.0, 0.6),
                Fields.BODY, new Bm25fSearcher.FieldParams(1.0, 0.9));
    }

    public static Map<String, Double> defaultBoosts() {
        return Map.of(Fields.TITLE, 1.0, Fields.BODY, 1.0);
    }

    public MutableIndex index() {
        return index;
    }

    public void add(Document document) {
        add(document, dense == null ? null : embed(document));
    }

    /** Adds a document with a precomputed embedding (null when dense retrieval is off). */
    public void add(Document document, float[] vector) {
        index.add(document);
        if (dense != null && vector != null) {
            dense.vectors().add(document.id(), vector);
        }
        synchronized (this) {
            if (suggesterReady) {
                suggester.add(document.title() + " " + document.content());
            }
        }
    }

    public boolean delete(int docId) {
        if (dense != null) {
            dense.vectors().delete(docId);
        }
        return index.delete(docId);
    }

    // ---- dense retrieval

    public enum Mode { KEYWORD, SEMANTIC, HYBRID }

    /** Candidates taken from each retriever before rank fusion. */
    static final int FUSION_DEPTH = 100;

    private record Dense(Encoder encoder, VectorIndex vectors) {
    }

    private volatile Dense dense;

    /** Enables semantic and hybrid modes; existing documents need vectors via {@link #add(Document, float[])}. */
    public void enableDense(Encoder encoder, VectorIndex vectors) {
        this.dense = new Dense(encoder, vectors);
    }

    public boolean denseEnabled() {
        return dense != null;
    }

    public float[] embed(Document document) {
        return dense.encoder().embed((document.title() + " " + document.content()).trim());
    }

    // ---- suggestions

    private final QuerySuggester suggester = new QuerySuggester();
    private boolean suggesterReady;

    /** Built lazily from the indexed documents (a persistent index is opened without re-adding them). */
    private synchronized QuerySuggester suggester() {
        if (!suggesterReady) {
            for (int id : index.docIds()) {
                index.document(id).ifPresent(d -> suggester.add(d.title() + " " + d.content()));
            }
            suggesterReady = true;
        }
        return suggester;
    }

    public String didYouMean(String query) {
        return suggester().didYouMean(query, index, index.analyzer());
    }

    public List<String> complete(String prefix, int limit) {
        return suggester().complete(prefix, limit);
    }

    public Query parse(String text) {
        return parser.parse(text);
    }

    /** Ranked ids only (no snippets) — what evaluation needs. */
    public TopDocs topDocs(String text, int k) {
        return topDocs(text, k, Mode.KEYWORD);
    }

    public TopDocs topDocs(String text, int k, Mode mode) {
        int window = Math.min(Math.max(k, 1), MAX_RESULT_WINDOW);
        if (mode != Mode.KEYWORD && dense == null) {
            throw new IllegalArgumentException("semantic search is not enabled (no embedding model)");
        }
        StaticPrior p = prior;
        String key = text.trim().toLowerCase(Locale.ROOT) + '\u0000' + window + '\u0000' + p.version() + '\u0000' + mode;
        return cache.get(index.generation(), key, () -> switch (mode) {
            case KEYWORD -> rerank(searcher.search(parse(text),
                    p.weight() == 0 ? window : Math.max(window, PRIOR_RERANK_DEPTH)), p, window);
            case SEMANTIC -> semantic(text, window);
            case HYBRID -> {
                int depth = Math.max(window, FUSION_DEPTH);
                TopDocs lexical = topDocs(text, depth, Mode.KEYWORD);
                TopDocs semantic = semantic(text, depth);
                List<TopDocs.ScoredDoc> fused = RankFusion.rrf(List.of(lexical.hits(), semantic.hits()),
                        RankFusion.DEFAULT_K, window);
                yield new TopDocs(Math.max(lexical.totalHits(), fused.size()), true, fused);
            }
        });
    }

    private TopDocs semantic(String text, int k) {
        Dense d = dense;
        List<TopDocs.ScoredDoc> hits = d.vectors().search(d.encoder().embed(text), k);
        return new TopDocs(hits.size(), true, hits);
    }

    // ---- query-independent prior (PageRank)

    /** Text hits re-scored with the prior: deep enough that the prior can promote, cheap enough to stay fast. */
    static final int PRIOR_RERANK_DEPTH = 100;

    /**
     * @param normalized per-doc prior where 1.0 is "average" (e.g. PageRank × N); missing docs count as 1.0
     * @param weight     multiplier of ln(prior) added to the text score; 0 disables
     */
    public record StaticPrior(Map<Integer, Double> normalized, double weight, long version) {
        static final StaticPrior NONE = new StaticPrior(Map.of(), 0, 0);

        double boost(int docId) {
            return weight * Math.log(Math.max(normalized.getOrDefault(docId, 1.0), 1e-6));
        }
    }

    private volatile StaticPrior prior = StaticPrior.NONE;

    public void setStaticPrior(Map<Integer, Double> normalized, double weight) {
        prior = new StaticPrior(Map.copyOf(normalized), weight, prior.version() + 1);
    }

    public StaticPrior staticPrior() {
        return prior;
    }

    private static TopDocs rerank(TopDocs text, StaticPrior p, int k) {
        if (p.weight() == 0) {
            return text;
        }
        List<TopDocs.ScoredDoc> rescored = new ArrayList<>(text.hits().size());
        for (TopDocs.ScoredDoc d : text.hits()) {
            rescored.add(new TopDocs.ScoredDoc(d.docId(), d.score() + p.boost(d.docId())));
        }
        rescored.sort(java.util.Comparator.comparingDouble(TopDocs.ScoredDoc::score).reversed()
                .thenComparingInt(TopDocs.ScoredDoc::docId));
        return new TopDocs(text.totalHits(), text.totalHitsIsLowerBound(), rescored.subList(0, Math.min(k, rescored.size())));
    }

    public Results search(String text, int page, int size) {
        return search(text, page, size, Mode.KEYWORD);
    }

    public Results search(String text, int page, int size, Mode mode) {
        long started = System.nanoTime();
        int from = (page - 1) * size;
        if (from >= MAX_RESULT_WINDOW) {
            return new Results(0, false, List.of(), elapsedMs(started), null);
        }
        TopDocs top = topDocs(text, from + size, mode);
        Predicate<String> highlight = highlighter(parse(text));
        List<Hit> hits = new ArrayList<>();
        for (int i = from; i < Math.min(from + size, top.hits().size()); i++) {
            TopDocs.ScoredDoc sd = top.hits().get(i);
            index.document(sd.docId()).ifPresent(doc ->
                    hits.add(new Hit(doc, sd.score(), snippets.generate(doc.content(), highlight))));
        }
        String suggestion = page == 1 ? didYouMean(text) : null;
        return new Results(top.totalHits(), top.totalHitsIsLowerBound(), hits, elapsedMs(started), suggestion);
    }

    /** Which index terms of a document should be highlighted for this query. */
    static Predicate<String> highlighter(Query query) {
        Set<String> exact = new HashSet<>();
        List<String> prefixes = new ArrayList<>();
        collect(query, exact, prefixes);
        return term -> exact.contains(term) || prefixes.stream().anyMatch(term::startsWith);
    }

    private static void collect(Query q, Set<String> exact, List<String> prefixes) {
        switch (q) {
            case Query.Term t -> exact.add(t.term());
            case Query.Phrase p -> exact.addAll(p.terms());
            case Query.Prefix p -> prefixes.add(p.prefix());
            case Query.Bool b -> b.clauses().stream()
                    .filter(c -> c.occur() != Query.Occur.MUST_NOT)
                    .forEach(c -> collect(c.query(), exact, prefixes));
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    public record Hit(Document document, double score, SnippetGenerator.Snippet snippet) {
    }

    /**
     * @param totalIsLowerBound true when WAND (or a top-k-only retriever) skipped documents
     * @param didYouMean        spelling-corrected query, or null
     */
    public record Results(long total, boolean totalIsLowerBound, List<Hit> hits, long tookMs, String didYouMean) {
    }
}
