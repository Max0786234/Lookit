package com.searchengine.crawler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PageRank by power iteration (Page, Brin et al. 1998) on a graph of documents.
 *
 * <pre>
 *   PR(v) = (1 − d) / N  +  d · ( Σ_{u → v} PR(u) / out(u)  +  dangling / N )
 * </pre>
 * Rank held by pages without outgoing links ("dangling") is spread uniformly so the vector stays
 * a probability distribution. Iterates until the L1 change is below {@code tolerance}.
 */
public final class PageRank {
    public static final double DAMPING = 0.85;

    public record Edge(int from, int to) {
    }

    public record Result(Map<Integer, Double> scores, int iterations) {
        /** Score scaled so the average page is 1.0 (independent of graph size). */
        public double normalized(int docId) {
            Double s = scores.get(docId);
            return s == null ? 1.0 : s * scores.size();
        }
    }

    private PageRank() {
    }

    /** @param nodes all documents; edges whose endpoints are not nodes, and self-links, are ignored */
    public static Result compute(int[] nodes, List<Edge> edges, double tolerance, int maxIterations) {
        int n = nodes.length;
        if (n == 0) {
            return new Result(Map.of(), 0);
        }
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(nodes[i], i);
        }
        // de-duplicate edges and build a compressed in-link adjacency
        java.util.Set<Long> unique = new java.util.HashSet<>();
        int[] outDegree = new int[n];
        int[] inCount = new int[n];
        for (Edge e : edges) {
            Integer f = index.get(e.from());
            Integer t = index.get(e.to());
            if (f != null && t != null && !f.equals(t) && unique.add(((long) f << 32) | t)) {
                outDegree[f]++;
                inCount[t]++;
            }
        }
        int[] inStart = new int[n + 1];
        for (int i = 0; i < n; i++) {
            inStart[i + 1] = inStart[i] + inCount[i];
        }
        int[] inFrom = new int[inStart[n]];
        int[] fill = Arrays.copyOf(inStart, n);
        for (long packed : unique) {
            int f = (int) (packed >>> 32);
            int t = (int) packed;
            inFrom[fill[t]++] = f;
        }

        double[] pr = new double[n];
        Arrays.fill(pr, 1.0 / n);
        double[] next = new double[n];
        int iterations = 0;
        while (iterations < maxIterations) {
            iterations++;
            double dangling = 0;
            for (int i = 0; i < n; i++) {
                if (outDegree[i] == 0) {
                    dangling += pr[i];
                }
            }
            double base = (1 - DAMPING) / n + DAMPING * dangling / n;
            double delta = 0;
            for (int v = 0; v < n; v++) {
                double sum = 0;
                for (int k = inStart[v]; k < inStart[v + 1]; k++) {
                    int u = inFrom[k];
                    sum += pr[u] / outDegree[u];
                }
                next[v] = base + DAMPING * sum;
                delta += Math.abs(next[v] - pr[v]);
            }
            double[] swap = pr;
            pr = next;
            next = swap;
            if (delta < tolerance) {
                break;
            }
        }
        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 0; i < n; i++) {
            scores.put(nodes[i], pr[i]);
        }
        return new Result(scores, iterations);
    }
}
