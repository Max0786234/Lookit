package com.searchengine.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (Cormack, Clarke &amp; Büttcher, SIGIR 2009):
 * {@code score(d) = Σ_lists 1 / (k + rank_list(d))} with ranks starting at 1 and k = 60.
 *
 * <p>Uses only ranks, so it fuses BM25 scores (unbounded) and cosine similarities (−1..1) without
 * any score normalisation or per-corpus tuning — which is why it is the default hybrid method.
 */
public final class RankFusion {
    public static final int DEFAULT_K = 60;

    private RankFusion() {
    }

    public static List<TopDocs.ScoredDoc> rrf(List<List<TopDocs.ScoredDoc>> rankings, int k, int limit) {
        Map<Integer, Double> fused = new HashMap<>();
        for (List<TopDocs.ScoredDoc> ranking : rankings) {
            for (int r = 0; r < ranking.size(); r++) {
                fused.merge(ranking.get(r).docId(), 1.0 / (k + r + 1), Double::sum);
            }
        }
        TopKCollector top = new TopKCollector(limit);
        fused.forEach(top::collect);
        return new ArrayList<>(top.topDocs(false).hits());
    }
}
