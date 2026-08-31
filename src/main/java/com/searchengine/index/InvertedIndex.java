package com.searchengine.index;

import com.searchengine.document.Document;
import com.searchengine.processor.TextProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InvertedIndex {
    private final TextProcessor textProcessor;
    private final Map<Integer, Document> documents = new HashMap<>();
    private final Map<Integer, Integer> documentLengths = new HashMap<>();
    private final Map<String, Map<Integer, Posting>> index = new HashMap<>();

    public InvertedIndex(TextProcessor textProcessor) {
        this.textProcessor = textProcessor;
    }

    public void addDocument(Document document) {
        documents.put(document.id(), document);

        List<String> tokens = new ArrayList<>();
        tokens.addAll(textProcessor.tokenize(document.title()));
        tokens.addAll(textProcessor.tokenize(document.content()));
        documentLengths.put(document.id(), tokens.size());

        for (int position = 0; position < tokens.size(); position++) {
            String token = tokens.get(position);
            Map<Integer, Posting> postingList = index.computeIfAbsent(token, ignored -> new HashMap<>());
            Posting posting = postingList.computeIfAbsent(document.id(), ignored -> new Posting(document.id()));
            posting.addPosition(position);
        }
    }

    public Collection<Posting> getPostings(String term) {
        return index.getOrDefault(term, Map.of()).values();
    }

    public Optional<Document> getDocument(int documentId) {
        return Optional.ofNullable(documents.get(documentId));
    }

    public int documentCount() {
        return documents.size();
    }

    public int vocabularySize() {
        return index.size();
    }

    public int documentFrequency(String term) {
        return index.getOrDefault(term, Map.of()).size();
    }

    public int documentLength(int documentId) {
        return documentLengths.getOrDefault(documentId, 0);
    }

    public double averageDocumentLength() {
        if (documentLengths.isEmpty()) {
            return 0.0;
        }

        int totalLength = documentLengths.values()
                .stream()
                .mapToInt(Integer::intValue)
                .sum();

        return (double) totalLength / documentLengths.size();
    }

    public Map<Integer, Posting> getPostingList(String term) {
        return Map.copyOf(index.getOrDefault(term, Map.of()));
    }

    public List<Document> getAllDocuments() {
        return new ArrayList<>(documents.values());
    }
}
