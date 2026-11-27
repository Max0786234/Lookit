package com.searchengine.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Runs every query of a dataset through a {@link Retriever} and averages the metrics. */
public final class Evaluator {

    /** Anything that can return a ranked list of corpus ids for a query string. */
    public interface Retriever {
        List<String> retrieve(String query, int k);
    }

    public record Report(String system, int queries, double ndcg10, double mrr10, double recall100,
                         double meanLatencyMs, double p95LatencyMs, double[] perQueryNdcg10) {

        public String markdownRow() {
            return String.format("| %s | %.4f | %.4f | %.4f | %.2f | %.2f |",
                    system, ndcg10, mrr10, recall100, meanLatencyMs, p95LatencyMs);
        }

        public static String markdownHeader() {
            return "| System | nDCG@10 | MRR@10 | Recall@100 | mean ms | p95 ms |\n|---|---|---|---|---|---|";
        }
    }

    public static Report evaluate(String system, BeirDataset dataset, Retriever retriever) {
        // Warm up the JIT so the first configuration measured is not penalised.
        dataset.queries().values().stream().limit(20).forEach(q -> retriever.retrieve(q, 100));

        double ndcg = 0;
        double mrr = 0;
        double recall = 0;
        List<Double> perQuery = new ArrayList<>();
        double[] latencies = new double[dataset.queries().size()];
        int i = 0;
        for (Map.Entry<String, String> q : dataset.queries().entrySet()) {
            Map<String, Integer> relevant = dataset.qrels().getOrDefault(q.getKey(), Map.of());
            long start = System.nanoTime();
            List<String> ranked = retriever.retrieve(q.getValue(), 100);
            latencies[i++] = (System.nanoTime() - start) / 1e6;
            double n = Metrics.ndcg(ranked, relevant, 10);
            perQuery.add(n);
            ndcg += n;
            mrr += Metrics.mrr(ranked, relevant, 10);
            recall += Metrics.recall(ranked, relevant, 100);
        }
        int count = dataset.queries().size();
        double[] sorted = latencies.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(latencies).average().orElse(0);
        double p95 = count == 0 ? 0 : sorted[Math.min(count - 1, (int) Math.ceil(0.95 * count) - 1)];
        return new Report(system, count, ndcg / count, mrr / count, recall / count, mean, p95,
                perQuery.stream().mapToDouble(Double::doubleValue).toArray());
    }
}
