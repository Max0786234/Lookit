package com.searchengine.eval;

import com.searchengine.analysis.Analyzer;
import com.searchengine.search.Bm25fSearcher;
import com.searchengine.search.RankFusion;
import com.searchengine.search.SearchEngine;
import com.searchengine.search.TopDocs;
import com.searchengine.vector.ExactVectorIndex;
import com.searchengine.vector.HnswIndex;
import com.searchengine.vector.OnnxEncoder;
import com.searchengine.vector.VectorIndex;
import com.searchengine.vector.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dense and hybrid evaluation systems. Corpus embeddings are computed once with all-MiniLM-L6-v2
 * and cached next to the dataset ({@code minilm.vec}); query embeddings are computed per query
 * inside the timed retrieval, as in production.
 */
final class DenseSystems {
    private static final Logger log = LoggerFactory.getLogger(DenseSystems.class);
    static final Path MODEL = Path.of("data/models/all-MiniLM-L6-v2");
    private static final int CANDIDATES = 100;

    private static OnnxEncoder encoder;
    private static final Map<String, Map<Integer, float[]>> CORPUS_VECTORS = new HashMap<>();

    private DenseSystems() {
    }

    static void register(Map<String, java.util.function.Function<BeirDataset, Evaluator.Retriever>> systems) {
        systems.put("dense-exact", ds -> dense(ds, new ExactVectorIndex()));
        systems.put("dense-hnsw", ds -> dense(ds, new HnswIndex()));
        systems.put("hybrid-rrf", DenseSystems::hybrid);
    }

    static synchronized OnnxEncoder encoder() {
        if (encoder == null) {
            try {
                encoder = new OnnxEncoder(MODEL);
            } catch (IOException e) {
                throw new UncheckedIOException("model missing: run scripts/fetch-model.sh", e);
            }
        }
        return encoder;
    }

    /** Embeddings of title + text for every corpus doc, keyed by position in the corpus. */
    static synchronized Map<Integer, float[]> corpusVectors(BeirDataset ds) {
        return CORPUS_VECTORS.computeIfAbsent(ds.dir().toString(), k -> {
            try {
                Path cache = ds.dir().resolve("minilm.vec");
                int dim = encoder().dimension();
                Map<Integer, float[]> vectors = VectorStore.read(cache, dim);
                if (vectors.size() == ds.corpus().size()) {
                    return vectors;
                }
                long t0 = System.nanoTime();
                List<String> texts = new ArrayList<>();
                for (BeirDataset.CorpusDoc d : ds.corpus()) {
                    texts.add((d.title() + " " + d.text()).trim());
                }
                float[][] embedded = encoder().embed(texts);
                Map<Integer, float[]> out = new LinkedHashMap<>();
                for (int i = 0; i < embedded.length; i++) {
                    out.put(i, embedded[i]);
                }
                java.nio.file.Files.deleteIfExists(cache);
                VectorStore.append(cache, dim, out);
                double secs = (System.nanoTime() - t0) / 1e9;
                log.info("Encoded {} docs in {} s ({} docs/s)", out.size(), String.format("%.1f", secs),
                        String.format("%.0f", out.size() / secs));
                return out;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static Evaluator.Retriever dense(BeirDataset ds, VectorIndex index) {
        Map<Integer, float[]> vectors = corpusVectors(ds);
        long t0 = System.nanoTime();
        vectors.forEach(index::add);
        log.info("{}: built over {} vectors in {} ms", index.getClass().getSimpleName(), vectors.size(),
                (System.nanoTime() - t0) / 1_000_000);
        return (query, k) -> toExternal(ds, index.search(encoder().embed(query), k));
    }

    private static Evaluator.Retriever hybrid(BeirDataset ds) {
        EvalRunner.IndexedCorpus corpus = EvalRunner.IndexedCorpus.build(ds, Analyzer.english());
        SearchEngine keyword = new SearchEngine(corpus.index(),
                new Bm25fSearcher(corpus.index(), SearchEngine.BM25F_K1, SearchEngine.defaultBm25fParams()));
        HnswIndex hnsw = new HnswIndex();
        corpusVectors(ds).forEach(hnsw::add);
        return (query, k) -> {
            List<TopDocs.ScoredDoc> lexical = keyword.topDocs(query, CANDIDATES).hits();
            List<TopDocs.ScoredDoc> semantic = hnsw.search(encoder().embed(query), CANDIDATES);
            return toExternal(ds, RankFusion.rrf(List.of(lexical, semantic), RankFusion.DEFAULT_K, k));
        };
    }

    private static List<String> toExternal(BeirDataset ds, List<TopDocs.ScoredDoc> hits) {
        return hits.stream().map(h -> ds.corpus().get(h.docId()).id()).toList();
    }
}
