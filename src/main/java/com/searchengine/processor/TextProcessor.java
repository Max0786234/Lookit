package com.searchengine.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TextProcessor {
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for",
            "from", "in", "is", "it", "of", "on", "or", "that", "the",
            "this", "to", "use", "using", "with"
    );

    public List<String> tokenize(String text) {
        String normalized = text
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ");

        String[] rawTokens = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>();

        for (String token : rawTokens) {
            if (!token.isBlank() && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }

        return tokens;
    }
}
