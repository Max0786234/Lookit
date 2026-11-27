package com.searchengine.search;

import com.searchengine.analysis.Analyzer;
import com.searchengine.index.IndexReader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spelling correction and autocomplete over the surface-word vocabulary of the indexed text.
 *
 * <p><b>Spelling.</b> A query word is only treated as misspelled when its <em>analyzed</em> form
 * matches no document, so morphological variants ("engines") are never "corrected". Candidates are
 * vocabulary words sharing a character trigram, filtered by Damerau–Levenshtein distance (≤ 1 for
 * words up to 4 letters, else ≤ 2) and ranked by distance, then corpus frequency.
 *
 * <p><b>Autocomplete.</b> Completes the last word of the query with the most frequent vocabulary
 * words that start with it, keeping the preceding words.
 */
public final class QuerySuggester {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final int MIN_WORD = 2;

    private final Analyzer surface = Analyzer.simple();
    private final TreeMap<String, Integer> frequency = new TreeMap<>();
    private final Map<String, List<String>> byTrigram = new HashMap<>();

    /** Counts the words of a document's text. */
    public synchronized void add(String text) {
        if (text == null) {
            return;
        }
        for (String w : surface.terms(text)) {
            if (w.length() < MIN_WORD || !w.codePoints().allMatch(Character::isLetter)) {
                continue;
            }
            if (frequency.merge(w, 1, Integer::sum) == 1) {
                for (String g : trigrams(w)) {
                    byTrigram.computeIfAbsent(g, k -> new ArrayList<>()).add(w);
                }
            }
        }
    }

    public synchronized int vocabularySize() {
        return frequency.size();
    }

    /**
     * @return the query with unknown words replaced by their best correction, or {@code null} when
     * every word is known or nothing better was found
     */
    public synchronized String didYouMean(String query, IndexReader reader, Analyzer analyzer) {
        Matcher m = WORD.matcher(query);
        StringBuilder out = new StringBuilder();
        int last = 0;
        boolean changed = false;
        while (m.find()) {
            String word = m.group();
            out.append(query, last, m.start());
            last = m.end();
            String replacement = word;
            if (!isOperator(word) && !isKnown(word, reader, analyzer)) {
                String fix = correct(word.toLowerCase(java.util.Locale.ROOT));
                if (fix != null) {
                    replacement = fix;
                    changed = true;
                }
            }
            out.append(replacement);
        }
        out.append(query.substring(last));
        return changed ? out.toString() : null;
    }

    private static boolean isOperator(String w) {
        return w.equals("AND") || w.equals("OR") || w.equals("NOT") || w.equals("title") || w.equals("body");
    }

    private static boolean isKnown(String word, IndexReader reader, Analyzer analyzer) {
        if (!word.codePoints().allMatch(Character::isLetter)) {
            return true; // numbers, codes: never "correct" them
        }
        String term = analyzer.normalize(word);
        if (term == null) {
            return true; // stopword
        }
        for (String f : reader.fields()) {
            if (reader.docFreq(f, term) > 0) {
                return true;
            }
        }
        return false;
    }

    String correct(String word) {
        if (word.length() < 3) {
            return null;
        }
        int maxDistance = word.length() <= 4 ? 1 : 2;
        Set<String> candidates = new HashSet<>();
        for (String g : trigrams(word)) {
            candidates.addAll(byTrigram.getOrDefault(g, List.of()));
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        int bestFreq = 0;
        for (String c : candidates) {
            if (Math.abs(c.length() - word.length()) > maxDistance) {
                continue;
            }
            int d = damerauLevenshtein(word, c, maxDistance);
            if (d > maxDistance) {
                continue;
            }
            int f = frequency.get(c);
            if (d < bestDistance || (d == bestDistance && f > bestFreq)) {
                best = c;
                bestDistance = d;
                bestFreq = f;
            }
        }
        return best == null || best.equals(word) ? null : best;
    }

    /** Completions for the last (partial) word, most frequent first, as full query strings. */
    public synchronized List<String> complete(String query, int limit) {
        String trimmed = query.stripTrailing();
        if (trimmed.isEmpty() || query.length() > trimmed.length()) {
            return List.of(); // nothing typed, or the user finished the word with a space
        }
        int split = trimmed.lastIndexOf(' ') + 1;
        String head = trimmed.substring(0, split);
        List<String> words = surface.terms(trimmed.substring(split));
        if (words.size() != 1) {
            return List.of();
        }
        String prefix = words.get(0);
        NavigableMap<String, Integer> range = frequency.subMap(prefix, true, prefix + Character.MAX_VALUE, false);
        return range.entrySet().stream()
                .filter(e -> !e.getKey().equals(prefix) || e.getValue() > 1)
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(e -> head + e.getKey())
                .toList();
    }

    private static List<String> trigrams(String w) {
        String padded = "$" + w + "$";
        List<String> out = new ArrayList<>();
        for (int i = 0; i + 3 <= padded.length(); i++) {
            out.add(padded.substring(i, i + 3));
        }
        return out;
    }

    /** Optimal-string-alignment distance with early exit once every cell exceeds {@code max}. */
    static int damerauLevenshtein(String a, String b, int max) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int rowMin = Integer.MAX_VALUE;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
                rowMin = Math.min(rowMin, d[i][j]);
            }
            if (rowMin > max) {
                return max + 1;
            }
        }
        return d[a.length()][b.length()];
    }
}
