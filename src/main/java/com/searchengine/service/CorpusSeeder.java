package com.searchengine.service;

import com.searchengine.document.DocumentEntity;
import com.searchengine.document.DocumentRepository;
import com.searchengine.eval.BeirDataset;
import com.searchengine.vector.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a BEIR corpus (e.g. SciFact's 5,183 scientific abstracts) into the database once, so a fresh
 * install has something worth searching. Documents get {@code beir://<name>/<id>} URLs. If the
 * dataset directory holds embeddings computed by the evaluation harness ({@code minilm.vec}, keyed
 * by corpus position), they are returned keyed by the new database ids so semantic search does not
 * have to re-encode the corpus.
 */
final class CorpusSeeder {
    private static final Logger log = LoggerFactory.getLogger(CorpusSeeder.class);
    static final int EMBEDDING_DIM = 384;
    private static final int BATCH = 500;

    private CorpusSeeder() {
    }

    /** @return reusable embeddings by document id (empty if nothing was seeded or none cached) */
    static Map<Integer, float[]> seedIfNeeded(DocumentRepository repository, String datasetDir) {
        if (datasetDir == null || datasetDir.isBlank()) {
            return Map.of();
        }
        Path dir = Path.of(datasetDir);
        if (!Files.exists(dir.resolve("corpus.jsonl.gz")) && !Files.exists(dir.resolve("corpus.jsonl"))) {
            log.info("No seed corpus at {} (scripts/fetch-beir.sh scifact downloads one)", dir);
            return Map.of();
        }
        String prefix = "beir://" + dir.getFileName() + "/";
        if (repository.existsByUrlStartingWith(prefix)) {
            return Map.of();
        }
        try {
            BeirDataset ds = BeirDataset.load(dir);
            long t0 = System.nanoTime();
            List<Integer> ids = new ArrayList<>(ds.corpus().size());
            List<DocumentEntity> batch = new ArrayList<>(BATCH);
            for (BeirDataset.CorpusDoc d : ds.corpus()) {
                String title = d.title().isBlank() ? firstWords(d.text()) : d.title();
                batch.add(new DocumentEntity(truncate(title, 500), prefix + d.id(), d.text()));
                if (batch.size() == BATCH) {
                    repository.saveAll(batch).forEach(e -> ids.add(e.getId()));
                    batch.clear();
                }
            }
            repository.saveAll(batch).forEach(e -> ids.add(e.getId()));
            log.info("Seeded {} documents from {} in {} ms", ids.size(), dir, (System.nanoTime() - t0) / 1_000_000);

            Map<Integer, float[]> vectors = new HashMap<>();
            Map<Integer, float[]> cached = VectorStore.read(dir.resolve("minilm.vec"), EMBEDDING_DIM);
            if (cached.size() == ids.size()) {
                cached.forEach((position, v) -> vectors.put(ids.get(position), v));
                log.info("Reusing {} cached embeddings for the seed corpus", vectors.size());
            }
            return vectors;
        } catch (IOException e) {
            log.warn("Could not seed from {}: {}", dir, e.toString());
            return Map.of();
        }
    }

    private static String firstWords(String text) {
        String[] words = text.trim().split("\\s+");
        String head = String.join(" ", java.util.Arrays.copyOf(words, Math.min(12, words.length)));
        return words.length > 12 ? head + "…" : head;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
