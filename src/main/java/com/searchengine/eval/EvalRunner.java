package com.searchengine.eval;

import com.searchengine.analysis.Analyzer;
import com.searchengine.analysis.Stopwords;
import com.searchengine.document.Document;
import com.searchengine.index.MemoryIndex;
import com.searchengine.search.Bm25Similarity;
import com.searchengine.search.SearchEngine;
import com.searchengine.search.Searcher;
import com.searchengine.search.Similarity;
import com.searchengine.search.TfIdfSimilarity;
import com.searchengine.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Command-line evaluation: {@code EvalRunner <dataset-dir> [output.md] [system,system,...]}.
 *
 * <p>Run with {@code mvn -q compile exec:java -Dexec.args="data/beir/scifact"}.
 */
public final class EvalRunner {
    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    /** Named system configurations; later milestones register more (BM25F, dense, hybrid). */
    public static final Map<String, Function<BeirDataset, Evaluator.Retriever>> SYSTEMS = new LinkedHashMap<>();

    static {
        SYSTEMS.put("tfidf", ds -> engine(ds, Analyzer.english(), new TfIdfSimilarity(), SearchEngine.defaultBoosts()));
        SYSTEMS.put("bm25-nostem", ds -> engine(ds, Analyzer.of(Stopwords.ENGLISH, false), new Bm25Similarity(),
                SearchEngine.defaultBoosts()));
        SYSTEMS.put("bm25", ds -> engine(ds, Analyzer.english(), new Bm25Similarity(), SearchEngine.defaultBoosts()));
    }

    private EvalRunner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: EvalRunner <dataset-dir> [output.md] [systems]");
        }
        Path dir = Path.of(args[0]);
        Path out = args.length > 1 ? Path.of(args[1]) : null;
        List<String> systems = args.length > 2 ? List.of(args[2].split(",")) : new ArrayList<>(SYSTEMS.keySet());

        BeirDataset dataset = BeirDataset.load(dir);
        log.info("Loaded {}: {} docs, {} test queries", dataset.name(), dataset.corpus().size(), dataset.queries().size());

        List<Evaluator.Report> reports = new ArrayList<>();
        for (String name : systems) {
            Function<BeirDataset, Evaluator.Retriever> factory = SYSTEMS.get(name);
            if (factory == null) {
                throw new IllegalArgumentException("unknown system " + name + "; known: " + SYSTEMS.keySet());
            }
            long t0 = System.nanoTime();
            Evaluator.Retriever retriever = factory.apply(dataset);
            log.info("{}: built in {} ms", name, (System.nanoTime() - t0) / 1_000_000);
            Evaluator.Report report = Evaluator.evaluate(name, dataset, retriever);
            log.info(report.markdownRow());
            reports.add(report);
        }
        String table = render(dataset, reports);
        if (out != null) {
            Files.writeString(out, table, StandardCharsets.UTF_8);
            log.info("Wrote {}", out);
        } else {
            log.info("\n{}", table);
        }
    }

    public static String render(BeirDataset dataset, List<Evaluator.Report> reports) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dataset: ").append(dataset.name()).append(" (").append(dataset.corpus().size())
                .append(" docs, ").append(dataset.queries().size()).append(" queries)\n\n");
        sb.append(Evaluator.Report.markdownHeader()).append('\n');
        reports.forEach(r -> sb.append(r.markdownRow()).append('\n'));
        return sb.toString();
    }

    /** Indexes the corpus with the given configuration and adapts the engine to a Retriever. */
    public static Evaluator.Retriever engine(BeirDataset ds, Analyzer analyzer, Similarity similarity,
                                             Map<String, Double> boosts) {
        IndexedCorpus corpus = IndexedCorpus.build(ds, analyzer);
        Searcher searcher = new Searcher(corpus.index(), similarity, boosts);
        SearchEngine engine = new SearchEngine(corpus.index(), searcher);
        return (query, k) -> corpus.toExternal(engine.topDocs(query, k));
    }

    /** A dataset loaded into a {@link MemoryIndex}, with BEIR string ids mapped to int doc ids. */
    public record IndexedCorpus(MemoryIndex index, String[] externalIds) {
        public static IndexedCorpus build(BeirDataset ds, Analyzer analyzer) {
            MemoryIndex index = new MemoryIndex(analyzer);
            String[] ids = new String[ds.corpus().size()];
            for (int i = 0; i < ids.length; i++) {
                BeirDataset.CorpusDoc d = ds.corpus().get(i);
                ids[i] = d.id();
                index.add(new Document(i, d.title(), "beir:" + d.id(), d.text()));
            }
            return new IndexedCorpus(index, ids);
        }

        public List<String> toExternal(TopDocs top) {
            return top.hits().stream().map(h -> externalIds[h.docId()]).toList();
        }
    }
}
