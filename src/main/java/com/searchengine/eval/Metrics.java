package com.searchengine.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Standard IR metrics over one ranked list, matching trec_eval / pytrec_eval conventions as used
 * by BEIR: graded gain {@code rel} (not 2^rel − 1) and log2(rank + 1) discount for nDCG.
 */
public final class Metrics {
    private Metrics() {
    }

    public static double ndcg(List<String> ranked, Map<String, Integer> relevant, int k) {
        double dcg = 0;
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            int rel = relevant.getOrDefault(ranked.get(i), 0);
            dcg += rel / log2(i + 2);
        }
        List<Integer> ideal = new ArrayList<>(relevant.values());
        ideal.sort(Comparator.reverseOrder());
        double idcg = 0;
        for (int i = 0; i < Math.min(k, ideal.size()); i++) {
            idcg += ideal.get(i) / log2(i + 2);
        }
        return idcg == 0 ? 0 : dcg / idcg;
    }

    /** Reciprocal rank of the first relevant hit within the top k (0 if none). */
    public static double mrr(List<String> ranked, Map<String, Integer> relevant, int k) {
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            if (relevant.getOrDefault(ranked.get(i), 0) > 0) {
                return 1.0 / (i + 1);
            }
        }
        return 0;
    }

    public static double recall(List<String> ranked, Map<String, Integer> relevant, int k) {
        if (relevant.isEmpty()) {
            return 0;
        }
        int found = 0;
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            if (relevant.getOrDefault(ranked.get(i), 0) > 0) {
                found++;
            }
        }
        return (double) found / relevant.size();
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
