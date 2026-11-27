package com.searchengine.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the window of {@code windowWords} consecutive words that covers the most distinct query
 * terms (ties: most total matches, then earliest) and reports highlight offsets inside it.
 *
 * <p>Words are matched after the same normalisation the index uses ({@code normalizer}), so a
 * query for "engines" highlights "engine" once stemming is on. Output is plain text plus offsets;
 * callers decide how to render highlights (no HTML is produced, so nothing needs escaping).
 */
public final class SnippetGenerator {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?");

    private final Function<String, String> normalizer;
    private final int windowWords;

    /**
     * @param normalizer maps a surface word to its index term, or {@code null} if it is not indexed
     */
    public SnippetGenerator(Function<String, String> normalizer, int windowWords) {
        this.normalizer = normalizer;
        this.windowWords = windowWords;
    }

    public Snippet generate(String text, Set<String> queryTerms) {
        return generate(text, queryTerms::contains);
    }

    /** @param matches whether an index term counts as a query hit (exact terms, or prefix expansion) */
    public Snippet generate(String text, Predicate<String> matches) {
        if (text == null || text.isEmpty()) {
            return new Snippet("", List.of());
        }
        List<int[]> words = new ArrayList<>(); // start, end
        List<String> terms = new ArrayList<>();
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            words.add(new int[]{m.start(), m.end()});
            String term = normalizer.apply(m.group());
            terms.add(term != null && matches.test(term) ? term : null);
        }
        if (words.isEmpty()) {
            return new Snippet(truncate(text), List.of());
        }

        int bestStart = 0;
        int bestDistinct = -1;
        int bestTotal = -1;
        int n = words.size();
        int window = Math.min(windowWords, n);
        for (int start = 0; start + window <= n; start++) {
            Set<String> distinct = new HashSet<>();
            int total = 0;
            for (int i = start; i < start + window; i++) {
                String t = terms.get(i);
                if (t != null) {
                    distinct.add(t);
                    total++;
                }
            }
            if (distinct.size() > bestDistinct || (distinct.size() == bestDistinct && total > bestTotal)) {
                bestStart = start;
                bestDistinct = distinct.size();
                bestTotal = total;
            }
        }

        int last = bestStart + window - 1;
        int from = bestStart == 0 ? 0 : words.get(bestStart)[0];
        int to = last == n - 1 ? text.length() : words.get(last)[1];
        String prefix = from > 0 ? "… " : "";
        String suffix = to < text.length() ? " …" : "";
        String body = text.substring(from, to).replaceAll("\\s+", " ");

        // Offsets must be computed on the whitespace-collapsed body, so re-scan it.
        List<Highlight> highlights = new ArrayList<>();
        Matcher bm = WORD.matcher(body);
        while (bm.find()) {
            String t = normalizer.apply(bm.group());
            if (t != null && matches.test(t)) {
                highlights.add(new Highlight(prefix.length() + bm.start(), prefix.length() + bm.end()));
            }
        }
        return new Snippet(prefix + body + suffix, highlights);
    }

    private String truncate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + " …";
    }

    public record Highlight(int start, int end) {
    }

    public record Snippet(String text, List<Highlight> highlights) {
    }
}
