package com.searchengine.eval;

import com.searchengine.search.TopDocs;
import com.searchengine.vector.ExactVectorIndex;
import com.searchengine.vector.HnswIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HNSW vs exact search on real MiniLM embeddings: recall@10 and latency for several efSearch
 * values. Query vectors are encoded once up front so only index search time is measured.
 * {@code mvn -q compile exec:java -Dexec.mainClass=com.searchengine.eval.VectorBenchmark -Dexec.args="data/beir/scifact"}
 */
public final class VectorBenchmark {
    private static final Logger log = LoggerFactory.getLogger(VectorBenchmark.class);

    private VectorBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        BeirDataset ds = BeirDataset.load(Path.of(args[0]));
        Map<Integer, float[]> corpus = DenseSystems.corpusVectors(ds);
        List<float[]> queries = new ArrayList<>();
        for (String q : ds.queries().values()) {
            queries.add(DenseSystems.encoder().embed(q));
        }

        ExactVectorIndex exact = new ExactVectorIndex();
        corpus.forEach(exact::add);
        long t0 = System.nanoTime();
        HnswIndex hnsw = new HnswIndex();
        corpus.forEach(hnsw::add);
        double buildSecs = (System.nanoTime() - t0) / 1e9;

        queries.forEach(q -> hnsw.search(q, 10)); // warm up the JIT before timing anything
        List<Set<Integer>> truth = new ArrayList<>();
        double exactMs = time(queries, q -> truth.add(ids(exact.search(q, 10))));

        StringBuilder md = new StringBuilder(String.format(
                "Dataset %s: %,d vectors (%d-d), %d queries; HNSW M=16 efConstruction=100 built in %.1f s%n%n",
                ds.name(), corpus.size(), corpus.values().iterator().next().length, queries.size(), buildSecs));
        md.append("| index | efSearch | recall@10 | mean ms/query |\n|---|---|---|---|\n");
        md.append(String.format("| exact (brute force) | — | 1.000 | %.3f |%n", exactMs));
        for (int ef : new int[]{10, 20, 40, 64, 100, 200}) {
            hnsw.setEfSearch(ef);
            double[] recall = {0};
            int[] i = {0};
            double ms = time(queries, q -> {
                Set<Integer> got = ids(hnsw.search(q, 10));
                got.retainAll(truth.get(i[0]++));
                recall[0] += got.size() / 10.0;
            });
            md.append(String.format("| HNSW | %d | %.3f | %.3f |%n", ef, recall[0] / queries.size(), ms));
        }
        log.info("\n{}", md);
    }

    private interface QueryAction {
        void run(float[] q);
    }

    /** Mean milliseconds per query. */
    private static double time(List<float[]> queries, QueryAction action) {
        long start = System.nanoTime();
        for (float[] q : queries) {
            action.run(q);
        }
        return (System.nanoTime() - start) / 1e6 / queries.size();
    }

    private static Set<Integer> ids(List<TopDocs.ScoredDoc> hits) {
        Set<Integer> s = new HashSet<>();
        hits.forEach(h -> s.add(h.docId()));
        return s;
    }
}
