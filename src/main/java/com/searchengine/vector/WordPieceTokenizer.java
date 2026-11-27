package com.searchengine.vector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BERT uncased tokenizer (BasicTokenizer + WordPiece), matching HuggingFace's
 * {@code BertTokenizer(do_lower_case=True)} used by sentence-transformers MiniLM models:
 * clean control chars, lower-case, strip accents, split on whitespace and punctuation, isolate CJK
 * characters, then greedy longest-match-first word pieces with a {@code ##} continuation prefix.
 */
public final class WordPieceTokenizer {
    private static final int MAX_CHARS_PER_WORD = 100;

    private final Map<String, Integer> vocab;
    private final int cls;
    private final int sep;
    private final int unk;

    public WordPieceTokenizer(Map<String, Integer> vocab) {
        this.vocab = vocab;
        this.cls = vocab.get("[CLS]");
        this.sep = vocab.get("[SEP]");
        this.unk = vocab.get("[UNK]");
    }

    public static WordPieceTokenizer load(Path vocabFile) throws IOException {
        Map<String, Integer> vocab = new HashMap<>();
        List<String> lines = Files.readAllLines(vocabFile, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            vocab.put(lines.get(i), i);
        }
        return new WordPieceTokenizer(vocab);
    }

    /** {@code [CLS] pieces… [SEP]}, truncated to {@code maxLength} ids including the specials. */
    public long[] encode(String text, int maxLength) {
        List<Integer> ids = new ArrayList<>();
        ids.add(cls);
        outer:
        for (String word : basicTokenize(text)) {
            for (int id : wordPiece(word)) {
                if (ids.size() >= maxLength - 1) {
                    break outer;
                }
                ids.add(id);
            }
        }
        ids.add(sep);
        return ids.stream().mapToLong(Integer::longValue).toArray();
    }

    /** Pieces as strings (for tests and debugging). */
    public List<String> tokenize(String text) {
        Map<Integer, String> inverse = new HashMap<>();
        vocab.forEach((k, v) -> inverse.put(v, k));
        List<String> out = new ArrayList<>();
        for (String word : basicTokenize(text)) {
            for (int id : wordPiece(word)) {
                out.add(inverse.get(id));
            }
        }
        return out;
    }

    static List<String> basicTokenize(String text) {
        StringBuilder cleaned = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (cp == 0 || cp == 0xFFFD || isControl(cp)) {
                return;
            }
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                cleaned.append(' ');
            } else if (isCjk(cp)) {
                cleaned.append(' ').appendCodePoint(cp).append(' ');
            } else {
                cleaned.appendCodePoint(cp);
            }
        });
        List<String> words = new ArrayList<>();
        for (String token : cleaned.toString().trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            String lower = stripAccents(token.toLowerCase(Locale.ROOT));
            StringBuilder current = new StringBuilder();
            lower.codePoints().forEach(cp -> {
                if (isPunctuation(cp)) {
                    if (!current.isEmpty()) {
                        words.add(current.toString());
                        current.setLength(0);
                    }
                    words.add(new String(Character.toChars(cp)));
                } else {
                    current.appendCodePoint(cp);
                }
            });
            if (!current.isEmpty()) {
                words.add(current.toString());
            }
        }
        return words;
    }

    private List<Integer> wordPiece(String word) {
        if (word.codePointCount(0, word.length()) > MAX_CHARS_PER_WORD) {
            return List.of(unk);
        }
        List<Integer> pieces = new ArrayList<>();
        int start = 0;
        while (start < word.length()) {
            int end = word.length();
            Integer found = null;
            while (start < end) {
                String sub = (start > 0 ? "##" : "") + word.substring(start, end);
                found = vocab.get(sub);
                if (found != null) {
                    break;
                }
                end = word.offsetByCodePoints(end, -1);
            }
            if (found == null) {
                return List.of(unk);
            }
            pieces.add(found);
            start = end;
        }
        return pieces;
    }

    private static String stripAccents(String s) {
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(nfd.length());
        nfd.codePoints().filter(cp -> Character.getType(cp) != Character.NON_SPACING_MARK).forEach(sb::appendCodePoint);
        return sb.toString();
    }

    private static boolean isControl(int cp) {
        if (cp == '\t' || cp == '\n' || cp == '\r') {
            return false;
        }
        int t = Character.getType(cp);
        return t == Character.CONTROL || t == Character.FORMAT;
    }

    /** BERT treats all non-alphanumeric ASCII as punctuation, plus Unicode P* categories. */
    private static boolean isPunctuation(int cp) {
        if ((cp >= 33 && cp <= 47) || (cp >= 58 && cp <= 64) || (cp >= 91 && cp <= 96) || (cp >= 123 && cp <= 126)) {
            return true;
        }
        int t = Character.getType(cp);
        return t == Character.CONNECTOR_PUNCTUATION || t == Character.DASH_PUNCTUATION
                || t == Character.START_PUNCTUATION || t == Character.END_PUNCTUATION
                || t == Character.INITIAL_QUOTE_PUNCTUATION || t == Character.FINAL_QUOTE_PUNCTUATION
                || t == Character.OTHER_PUNCTUATION;
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF) || (cp >= 0x20000 && cp <= 0x2A6DF)
                || (cp >= 0x2A700 && cp <= 0x2B73F) || (cp >= 0x2B740 && cp <= 0x2B81F)
                || (cp >= 0x2B820 && cp <= 0x2CEAF) || (cp >= 0xF900 && cp <= 0xFAFF) || (cp >= 0x2F800 && cp <= 0x2FA1F);
    }
}
