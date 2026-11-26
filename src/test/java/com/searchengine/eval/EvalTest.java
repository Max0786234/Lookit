package com.searchengine.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvalTest {

    private static final double EPS = 1e-9;

    @Test
    void ndcgMatchesHandComputation() {
        Map<String, Integer> rel = Map.of("a", 2, "b", 1);
        // ranked: x, a, b → DCG = 2/log2(3) + 1/log2(4); IDCG = 2/log2(2) + 1/log2(3)
        double dcg = 2 / log2(3) + 1 / log2(4);
        double idcg = 2 / log2(2) + 1 / log2(3);
        assertEquals(dcg / idcg, Metrics.ndcg(List.of("x", "a", "b"), rel, 10), EPS);
        assertEquals(1.0, Metrics.ndcg(List.of("a", "b"), rel, 10), EPS);
        assertEquals(0.0, Metrics.ndcg(List.of("x", "y"), rel, 10), EPS);
        // cut-off excludes later hits
        assertEquals(0.0, Metrics.ndcg(List.of("x", "a"), rel, 1), EPS);
    }

    @Test
    void mrrAndRecall() {
        Map<String, Integer> rel = Map.of("a", 1, "b", 1);
        assertEquals(1.0 / 3, Metrics.mrr(List.of("x", "y", "b", "a"), rel, 10), EPS);
        assertEquals(0.0, Metrics.mrr(List.of("x", "y", "b"), rel, 2), EPS);
        assertEquals(0.5, Metrics.recall(List.of("x", "a"), rel, 100), EPS);
    }

    @Test
    void loadsBeirDirectoryAndKeepsOnlyJudgedQueries() throws Exception {
        BeirDataset ds = BeirDataset.load(Path.of("src/test/resources/beir/tiny"));
        assertEquals(3, ds.corpus().size());
        assertEquals(List.of("q1", "q2"), List.copyOf(ds.queries().keySet()));
        assertEquals(Map.of("d1", 2, "d3", 1), ds.qrels().get("q1"));
        assertEquals(Map.of("d2", 1), ds.qrels().get("q2"), "zero-grade judgments are not relevant");
    }

    @Test
    void endToEndEvaluationOnTinyDataset() throws Exception {
        BeirDataset ds = BeirDataset.load(Path.of("src/test/resources/beir/tiny"));
        Evaluator.Report report = Evaluator.evaluate("bm25", ds, EvalRunner.SYSTEMS.get("bm25").apply(ds));
        assertEquals(2, report.queries());
        assertEquals(1.0, report.mrr10(), EPS);
        assertEquals(1.0, report.recall100(), EPS);
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
