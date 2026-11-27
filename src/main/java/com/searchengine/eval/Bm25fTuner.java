package com.searchengine.eval;

import com.searchengine.analysis.Analyzer;
import com.searchengine.index.Fields;
import com.searchengine.search.Bm25fSearcher;
import com.searchengine.search.Bm25fSearcher.FieldParams;
import com.searchengine.search.SearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Grid search of BM25F parameters on a dataset's <em>train</em> split (never test), so the
 * reported test numbers are not tuned on the queries they are measured on.
 *
 * <p>{@code mvn -q compile exec:java -Dexec.mainClass=com.searchengine.eval.Bm25fTuner
 * -Dexec.args="data/beir/scifact"}
 */
public final class Bm25fTuner {
    private static final Logger log = LoggerFactory.getLogger(Bm25fTuner.class);

    private Bm25fTuner() {
    }

    public static void main(String[] args) throws IOException {
        BeirDataset train = BeirDataset.load(Path.of(args[0]), "train");
        EvalRunner.IndexedCorpus corpus = EvalRunner.IndexedCorpus.build(train, Analyzer.english());
        log.info("Tuning on {} train queries", train.queries().size());

        double best = -1;
        String bestConfig = "";
        for (double k1 : new double[]{0.6, 0.75, 0.9, 1.2, 1.5}) {
            for (double wTitle : new double[]{0.5, 1.0, 1.5, 2.0, 3.0}) {
                for (double bTitle : new double[]{0.3, 0.6, 0.9}) {
                    for (double bBody : new double[]{0.6, 0.75, 0.9}) {
                        Map<String, FieldParams> params = Map.of(
                                Fields.TITLE, new FieldParams(wTitle, bTitle),
                                Fields.BODY, new FieldParams(1.0, bBody));
                        SearchEngine engine = new SearchEngine(corpus.index(),
                                new Bm25fSearcher(corpus.index(), k1, params));
                        Evaluator.Report r = Evaluator.evaluate("bm25f", train,
                                (q, k) -> corpus.toExternal(engine.topDocs(q, k)));
                        String config = String.format("k1=%.2f wTitle=%.1f bTitle=%.2f bBody=%.2f", k1, wTitle, bTitle, bBody);
                        if (r.ndcg10() > best) {
                            best = r.ndcg10();
                            bestConfig = config;
                            log.info("new best nDCG@10={} {}", String.format("%.4f", best), config);
                        }
                    }
                }
            }
        }
        log.info("BEST on train: nDCG@10={} {}", String.format("%.4f", best), bestConfig);
    }
}
