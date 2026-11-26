package com.searchengine.search;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;
import com.searchengine.index.Fields;
import com.searchengine.index.MemoryIndex;
import com.searchengine.index.MutableIndex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Spring-free engine facade: index + query parsing + ranking + snippets + result cache.
 * The REST layer, the evaluation harness and benchmarks all use this class.
 */
public final class SearchEngine {
    /** Deepest result rank that can be paged to. */
    public static final int MAX_RESULT_WINDOW = 1000;
    static final int SNIPPET_WORDS = 30;

    private final MutableIndex index;
    private final Searcher searcher;
    private final SnippetGenerator snippets;
    private final QueryCache<TopDocs> cache = new QueryCache<>(512);

    public SearchEngine(MutableIndex index, Searcher searcher) {
        this.index = index;
        this.searcher = searcher;
        Analyzer analyzer = index.analyzer();
        this.snippets = new SnippetGenerator(analyzer::normalize, SNIPPET_WORDS);
    }

    /** English analyzer, in-memory index, BM25 over title and body. */
    public static SearchEngine inMemory() {
        MemoryIndex index = new MemoryIndex(Analyzer.english());
        return new SearchEngine(index, new Searcher(index, new Bm25Similarity(), defaultBoosts()));
    }

    public static Map<String, Double> defaultBoosts() {
        return Map.of(Fields.TITLE, 1.0, Fields.BODY, 1.0);
    }

    public MutableIndex index() {
        return index;
    }

    public void add(Document document) {
        index.add(document);
    }

    public boolean delete(int docId) {
        return index.delete(docId);
    }

    public Query parse(String text) {
        String trimmed = text.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return QueryBuilders.phrase(index.analyzer(), null, trimmed.substring(1, trimmed.length() - 1));
        }
        return QueryBuilders.bagOfWords(index.analyzer(), trimmed);
    }

    /** Ranked ids only (no snippets) — what evaluation needs. */
    public TopDocs topDocs(String text, int k) {
        int window = Math.min(Math.max(k, 1), MAX_RESULT_WINDOW);
        String key = text.trim().toLowerCase(Locale.ROOT) + '\u0000' + window;
        return cache.get(index.generation(), key, () -> searcher.search(parse(text), window));
    }

    public Results search(String text, int page, int size) {
        long started = System.nanoTime();
        int from = (page - 1) * size;
        if (from >= MAX_RESULT_WINDOW) {
            return new Results(0, List.of(), elapsedMs(started));
        }
        TopDocs top = topDocs(text, from + size);
        Predicate<String> highlight = highlighter(parse(text));
        List<Hit> hits = new ArrayList<>();
        for (int i = from; i < Math.min(from + size, top.hits().size()); i++) {
            TopDocs.ScoredDoc sd = top.hits().get(i);
            index.document(sd.docId()).ifPresent(doc ->
                    hits.add(new Hit(doc, sd.score(), snippets.generate(doc.content(), highlight))));
        }
        return new Results(top.totalHits(), hits, elapsedMs(started));
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

    public record Results(long total, List<Hit> hits, long tookMs) {
    }
}
