package com.searchengine.eval;

import com.searchengine.analysis.Analyzer;
import com.searchengine.document.Document;
import com.searchengine.index.Fields;
import com.searchengine.index.IndexReader;
import com.searchengine.index.MemoryIndex;
import com.searchengine.index.SegmentedIndex;
import com.searchengine.search.Bm25fSearcher;
import com.searchengine.search.Query;
import com.searchengine.search.QueryParser;
import com.searchengine.search.SearchEngine;
import com.searchengine.search.Searcher;
import com.searchengine.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Indexing and query performance benchmark over a BEIR corpus:
 * {@code mvn -q compile exec:java -Dexec.mainClass=com.searchengine.eval.Benchmark
 * -Dexec.args="data/beir/fiqa [out.md]"}.
 */
public final class Benchmark {
    private static final Logger log = LoggerFactory.getLogger(Benchmark.class);
    private static final int K = 10;
    private static final int PASSES = 3;

    private Benchmark() {
    }

    public static void main(String[] args) throws Exception {
        BeirDataset ds = BeirDataset.load(Path.of(args[0]));
        StringBuilder md = new StringBuilder();
        md.append("Dataset: ").append(ds.name()).append(" (").append(ds.corpus().size()).append(" docs, ")
                .append(ds.queries().size()).append(" queries), k=").append(K).append(", JDK ")
                .append(Runtime.version()).append(", ").append(Runtime.getRuntime().availableProcessors())
                .append(" cores\n\n");

        long rawBytes = ds.corpus().stream()
                .mapToLong(d -> (d.title() + d.text()).getBytes(StandardCharsets.UTF_8).length).sum();

        // --- indexing
        System.gc();
        long t0 = System.nanoTime();
        MemoryIndex mem = new MemoryIndex(Analyzer.english());
        for (int i = 0; i < ds.corpus().size(); i++) {
            BeirDataset.CorpusDoc d = ds.corpus().get(i);
            mem.add(new Document(i, d.title(), "beir:" + d.id(), d.text()));
        }
        double memSecs = (System.nanoTime() - t0) / 1e9;

        Path dir = Files.createTempDirectory("lookit-bench");
        t0 = System.nanoTime();
        SegmentedIndex seg = SegmentedIndex.open(dir, Analyzer.english(), 10_000, 8, 4);
        for (int i = 0; i < ds.corpus().size(); i++) {
            BeirDataset.CorpusDoc d = ds.corpus().get(i);
            seg.add(new Document(i, d.title(), "beir:" + d.id(), d.text()));
        }
        seg.commit();
        double segSecs = (System.nanoTime() - t0) / 1e9;
        int segmentsBeforeMerge = seg.segmentCount();
        t0 = System.nanoTime();
        seg.forceMerge();
        seg.commit();
        double mergeSecs = (System.nanoTime() - t0) / 1e9;
        long indexBytes = sizeOf(dir);

        md.append("### Indexing\n\n| | value |\n|---|---|\n");
        md.append(String.format("| raw text (title+body, UTF-8) | %.1f MB |%n", rawBytes / 1e6));
        md.append(String.format("| in-memory build | %.2f s (%,.0f docs/s) |%n", memSecs, ds.corpus().size() / memSecs));
        md.append(String.format("| segmented build incl. flushes + commit | %.2f s (%,.0f docs/s), %d segments |%n",
                segSecs, ds.corpus().size() / segSecs, segmentsBeforeMerge));
        md.append(String.format("| force-merge to 1 segment | %.2f s |%n", mergeSecs));
        md.append(String.format("| on-disk index (stored docs + positional postings) | %.1f MB (%.0f%% of raw) |%n",
                indexBytes / 1e6, 100.0 * indexBytes / rawBytes));
        md.append(String.format("| vocabulary (body) | %,d terms |%n%n", mem.vocabularySize(Fields.BODY)));

        // --- queries
        QueryParser parser = new QueryParser(Analyzer.english(), Set.copyOf(Fields.ALL));
        List<Query> queries = ds.queries().values().stream().map(parser::parse).toList();
        md.append("### Query latency (single thread)\n\n")
                .append("| index | evaluation | mean ms | p50 | p95 | p99 | QPS | docs scored / query |\n")
                .append("|---|---|---|---|---|---|---|---|\n");
        for (Object[] cfg : new Object[][]{{"memory", mem}, {"mmap segment", seg}}) {
            for (boolean wand : new boolean[]{false, true}) {
                Searcher s = searcher((IndexReader) cfg[1], wand);
                md.append(latencyRow((String) cfg[0], wand ? "WAND" : "exhaustive", s, queries)).append('\n');
            }
        }
        md.append('\n').append(throughput(searcher(seg, true), queries, 4)).append('\n');
        seg.close();
        log.info("\n{}", md);
        if (args.length > 1) {
            Files.writeString(Path.of(args[1]), md, StandardCharsets.UTF_8);
        }
    }

    private static Searcher searcher(IndexReader reader, boolean wand) {
        return new Bm25fSearcher(reader, SearchEngine.BM25F_K1, SearchEngine.defaultBm25fParams()).wand(wand);
    }

    private static String latencyRow(String index, String mode, Searcher s, List<Query> queries) {
        queries.forEach(q -> s.search(q, K)); // warm-up
        double[] lat = new double[queries.size() * PASSES];
        long scored = 0;
        int n = 0;
        long start = System.nanoTime();
        for (int p = 0; p < PASSES; p++) {
            for (Query q : queries) {
                long t = System.nanoTime();
                TopDocs top = s.search(q, K);
                lat[n++] = (System.nanoTime() - t) / 1e6;
                scored += top.totalHits();
            }
        }
        double secs = (System.nanoTime() - start) / 1e9;
        Arrays.sort(lat);
        return String.format("| %s | %s | %.3f | %.3f | %.3f | %.3f | %,.0f | %,.0f |", index, mode,
                Arrays.stream(lat).average().orElse(0), pct(lat, 50), pct(lat, 95), pct(lat, 99),
                n / secs, (double) scored / n);
    }

    private static String throughput(Searcher s, List<Query> queries, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger done = new AtomicInteger();
        int total = queries.size() * PASSES * threads;
        long start = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                for (int p = 0; p < PASSES; p++) {
                    for (Query q : queries) {
                        s.search(q, K);
                        done.incrementAndGet();
                    }
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        double secs = (System.nanoTime() - start) / 1e9;
        return String.format("Throughput, mmap segment + WAND, %d threads: **%,.0f QPS** (%d queries in %.2f s)",
                threads, total / secs, done.get(), secs);
    }

    private static double pct(double[] sorted, int p) {
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p / 100.0 * sorted.length) - 1)];
    }

    private static long sizeOf(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
        }
    }
}
