package com.searchengine.eval;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lucene BM25 reference systems for the evaluation table. Test-scope only (ADR-001):
 * {@code mvn -q test-compile exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=com.searchengine.eval.LuceneBaseline -Dexec.args="data/beir/scifact out.md"}.
 */
public final class LuceneBaseline {

    private LuceneBaseline() {
    }

    public static void main(String[] args) throws IOException {
        EvalRunner.SYSTEMS.put("lucene-bm25-flat", ds -> lucene(ds, false));
        EvalRunner.SYSTEMS.put("lucene-bm25-multifield", ds -> lucene(ds, true));
        EvalRunner.main(args);
    }

    /**
     * @param multiField index title and body as separate fields (like our engine) rather than one
     *                   concatenated "contents" field (Pyserini's BEIR "flat" setting)
     */
    static Evaluator.Retriever lucene(BeirDataset ds, boolean multiField) {
        try {
            EnglishAnalyzer analyzer = new EnglishAnalyzer();
            ByteBuffersDirectory dir = new ByteBuffersDirectory();
            IndexWriterConfig cfg = new IndexWriterConfig(analyzer).setSimilarity(new BM25Similarity(1.2f, 0.75f));
            try (IndexWriter w = new IndexWriter(dir, cfg)) {
                for (BeirDataset.CorpusDoc d : ds.corpus()) {
                    Document doc = new Document();
                    doc.add(new StoredField("id", d.id()));
                    if (multiField) {
                        doc.add(new TextField("title", d.title(), Field.Store.NO));
                        doc.add(new TextField("body", d.text(), Field.Store.NO));
                    } else {
                        doc.add(new TextField("contents", d.title() + " " + d.text(), Field.Store.NO));
                    }
                    w.addDocument(doc);
                }
            }
            IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(dir));
            searcher.setSimilarity(new BM25Similarity(1.2f, 0.75f));
            QueryParser parser = multiField
                    ? new MultiFieldQueryParser(new String[]{"title", "body"}, analyzer)
                    : new QueryParser("contents", analyzer);
            return (query, k) -> {
                try {
                    ScoreDoc[] hits = searcher.search(parser.parse(QueryParser.escape(query)), k).scoreDocs;
                    List<String> ids = new ArrayList<>(hits.length);
                    for (ScoreDoc h : hits) {
                        ids.add(searcher.storedFields().document(h.doc).get("id"));
                    }
                    return ids;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
