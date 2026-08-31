package com.searchengine.query;

import com.searchengine.document.Document;
import com.searchengine.index.InvertedIndex;
import com.searchengine.index.Posting;
import com.searchengine.processor.TextProcessor;
import com.searchengine.ranking.Ranker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SearchService {
    private final InvertedIndex index;
    private final TextProcessor textProcessor;
    private final Ranker ranker;

    public SearchService(InvertedIndex index, TextProcessor textProcessor, Ranker ranker) {
        this.index = index;
        this.textProcessor = textProcessor;
        this.ranker = ranker;
    }

    public List<SearchResult> search(String query) {
        if (isPhraseQuery(query)) {
            return searchPhrase(query);
        }

        List<String> queryTerms = textProcessor.tokenize(query);
        Map<Integer, Double> scoresByDocumentId = new HashMap<>();

        for (String term : queryTerms) {
            for (Posting posting : index.getPostings(term)) {
                double scoreContribution = ranker.score(term, posting, index);
                scoresByDocumentId.merge(posting.documentId(), scoreContribution, Double::sum);
            }
        }

        List<SearchResult> results = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : scoresByDocumentId.entrySet()) {
            Document document = index.getDocument(entry.getKey())
                    .orElseThrow(() -> new IllegalStateException("Missing document for id " + entry.getKey()));
            results.add(new SearchResult(document, entry.getValue()));
        }

        results.sort(Comparator.comparingDouble(SearchResult::score).reversed());
        return results;
    }

    private boolean isPhraseQuery(String query) {
        String trimmedQuery = query.trim();
        return trimmedQuery.length() >= 2
                && trimmedQuery.startsWith("\"")
                && trimmedQuery.endsWith("\"");
    }

    private List<SearchResult> searchPhrase(String query) {
        String phrase = query.trim().substring(1, query.trim().length() - 1);
        List<String> phraseTerms = textProcessor.tokenize(phrase);

        if (phraseTerms.isEmpty()) {
            return List.of();
        }

        Map<Integer, Posting> firstTermPostings = index.getPostingList(phraseTerms.get(0));
        List<SearchResult> results = new ArrayList<>();

        for (Posting firstPosting : firstTermPostings.values()) {
            int documentId = firstPosting.documentId();
            int phraseFrequency = countPhraseMatches(documentId, phraseTerms, firstPosting);

            if (phraseFrequency > 0) {
                Document document = index.getDocument(documentId)
                        .orElseThrow(() -> new IllegalStateException("Missing document for id " + documentId));
                results.add(new SearchResult(document, phraseFrequency));
            }
        }

        results.sort(Comparator.comparingDouble(SearchResult::score).reversed());
        return results;
    }

    private int countPhraseMatches(int documentId, List<String> phraseTerms, Posting firstPosting) {
        List<Set<Integer>> remainingTermPositions = new ArrayList<>();

        for (int termIndex = 1; termIndex < phraseTerms.size(); termIndex++) {
            Posting posting = index.getPostingList(phraseTerms.get(termIndex)).get(documentId);
            if (posting == null) {
                return 0;
            }

            remainingTermPositions.add(new HashSet<>(posting.positions()));
        }

        int matches = 0;
        for (int startPosition : firstPosting.positions()) {
            if (isPhraseMatchAt(startPosition, remainingTermPositions)) {
                matches++;
            }
        }

        return matches;
    }

    private boolean isPhraseMatchAt(int startPosition, List<Set<Integer>> remainingTermPositions) {
        for (int offset = 1; offset <= remainingTermPositions.size(); offset++) {
            if (!remainingTermPositions.get(offset - 1).contains(startPosition + offset)) {
                return false;
            }
        }

        return true;
    }
}
