package com.searchengine.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * A BEIR-format dataset directory: {@code corpus.jsonl[.gz]}, {@code queries.jsonl[.gz]} and
 * {@code test.tsv} (or {@code qrels/test.tsv}) with {@code query-id  corpus-id  score} rows.
 */
public record BeirDataset(String name, List<CorpusDoc> corpus, Map<String, String> queries,
                          Map<String, Map<String, Integer>> qrels) {

    public record CorpusDoc(String id, String title, String text) {
    }

    public static BeirDataset load(Path dir) throws IOException {
        return load(dir, "test");
    }

    /** @param split qrels split to load ("test", "train", "dev"); queries are filtered to it */
    public static BeirDataset load(Path dir, String split) throws IOException {
        ObjectMapper json = new ObjectMapper();
        List<CorpusDoc> corpus = new ArrayList<>();
        try (BufferedReader r = open(dir, "corpus.jsonl")) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode n = json.readTree(line);
                corpus.add(new CorpusDoc(n.get("_id").asText(), n.path("title").asText(""), n.path("text").asText("")));
            }
        }

        Map<String, Map<String, Integer>> qrels = new LinkedHashMap<>();
        Path qrelsFile = Files.exists(dir.resolve(split + ".tsv")) ? dir.resolve(split + ".tsv") : dir.resolve("qrels/" + split + ".tsv");
        for (String line : Files.readAllLines(qrelsFile, StandardCharsets.UTF_8)) {
            String[] parts = line.split("\t");
            if (parts.length < 3 || parts[0].equals("query-id")) {
                continue;
            }
            int grade = Integer.parseInt(parts[2].trim());
            if (grade > 0) {
                qrels.computeIfAbsent(parts[0], k -> new HashMap<>()).put(parts[1], grade);
            }
        }

        Map<String, String> queries = new LinkedHashMap<>();
        try (BufferedReader r = open(dir, "queries.jsonl")) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode n = json.readTree(line);
                String id = n.get("_id").asText();
                if (qrels.containsKey(id)) {
                    queries.put(id, n.get("text").asText());
                }
            }
        }
        return new BeirDataset(dir.getFileName() + (split.equals("test") ? "" : "/" + split), corpus, queries, qrels);
    }

    private static BufferedReader open(Path dir, String name) throws IOException {
        Path plain = dir.resolve(name);
        InputStream in = Files.exists(plain)
                ? Files.newInputStream(plain)
                : new GZIPInputStream(Files.newInputStream(dir.resolve(name + ".gz")), 1 << 16);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
    }
}
