package com.searchengine.analysis;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text → tokens pipeline: Unicode word segmentation, lower-casing, Latin accent folding,
 * possessive stripping, stopword removal and (optionally) Porter stemming.
 *
 * <p>Han / Hiragana / Katakana runs are emitted one character per token (unigrams) since those
 * scripts do not separate words with spaces. Other scripts (e.g. Devanagari) keep their combining
 * marks; only marks on Latin letters are folded ("café" → "cafe").
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class Analyzer {
    private static final Pattern WORD =
            Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*(?:['’][\\p{L}\\p{M}\\p{N}]+)*");
    private static final int MAX_TOKEN_LENGTH = 64;

    private final Set<String> stopwords;
    private final boolean stem;

    private Analyzer(Set<String> stopwords, boolean stem) {
        this.stopwords = stopwords;
        this.stem = stem;
    }

    /** Stopwords removed and Porter stemming: the default for English retrieval. */
    public static Analyzer english() {
        return new Analyzer(Stopwords.ENGLISH, true);
    }

    /** Lower-case + folding only. Used for exact matching and autocomplete over surface forms. */
    public static Analyzer simple() {
        return new Analyzer(Set.of(), false);
    }

    public static Analyzer of(Set<String> stopwords, boolean stem) {
        return new Analyzer(Set.copyOf(stopwords), stem);
    }

    public List<Token> analyze(String text) {
        List<Token> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        PorterStemmer stemmer = stem ? new PorterStemmer() : null;
        Matcher m = WORD.matcher(text);
        int position = 0;
        while (m.find()) {
            String word = m.group();
            if (isCjk(word.codePointAt(0))) {
                for (int i = 0; i < word.length(); ) {
                    int cp = word.codePointAt(i);
                    int len = Character.charCount(cp);
                    tokens.add(new Token(new String(Character.toChars(cp)), position++,
                            m.start() + i, m.start() + i + len));
                    i += len;
                }
                continue;
            }
            String term = normalizeSurface(word);
            if (term.length() > MAX_TOKEN_LENGTH || stopwords.contains(term)) {
                position++;
                continue;
            }
            if (stemmer != null) {
                term = stemmer.stem(term);
            }
            tokens.add(new Token(term, position++, m.start(), m.end()));
        }
        return tokens;
    }

    public List<String> terms(String text) {
        return analyze(text).stream().map(Token::term).toList();
    }

    /** Index term for a single surface word, or {@code null} if the analyzer drops it. */
    public String normalize(String word) {
        List<Token> tokens = analyze(word);
        return tokens.size() == 1 ? tokens.get(0).term() : null;
    }

    /** Lower-case, fold Latin diacritics, strip possessive "'s" and remaining apostrophes. */
    static String normalizeSurface(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        String folded = foldLatin(lower);
        if (folded.endsWith("'s") || folded.endsWith("’s")) {
            folded = folded.substring(0, folded.length() - 2);
        }
        return folded.replace("'", "").replace("’", "");
    }

    private static String foldLatin(String s) {
        boolean ascii = true;
        for (int i = 0; i < s.length() && ascii; i++) {
            ascii = s.charAt(i) < 128;
        }
        if (ascii) {
            return s;
        }
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder(nfd.length());
        Character.UnicodeScript baseScript = null;
        for (int i = 0; i < nfd.length(); ) {
            int cp = nfd.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.getType(cp) == Character.NON_SPACING_MARK) {
                if (baseScript == Character.UnicodeScript.LATIN) {
                    continue;
                }
            } else {
                baseScript = Character.UnicodeScript.of(cp);
            }
            out.appendCodePoint(cp);
        }
        return Normalizer.normalize(fold(out), Normalizer.Form.NFC);
    }

    private static String fold(StringBuilder sb) {
        // letters NFD does not decompose
        return sb.toString().replace("ß", "ss").replace("æ", "ae").replace("ø", "o")
                .replace("œ", "oe").replace("ł", "l").replace("đ", "d");
    }

    private static boolean isCjk(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }
}
