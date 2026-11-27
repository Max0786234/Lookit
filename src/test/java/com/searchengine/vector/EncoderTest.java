package com.searchengine.vector;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EncoderTest {

    static final Path MODEL = Path.of("data/models/all-MiniLM-L6-v2");

    @Test
    void wordPieceGreedyLongestMatchWithTinyVocab() {
        Map<String, Integer> vocab = Map.of("[PAD]", 0, "[UNK]", 1, "[CLS]", 2, "[SEP]", 3,
                "un", 4, "##aff", 5, "##able", 6, "!", 7, "cafe", 8);
        WordPieceTokenizer t = new WordPieceTokenizer(vocab);
        assertEquals(List.of("un", "##aff", "##able", "!", "cafe", "[UNK]"), t.tokenize("UNaffable! Café xyz"));
        assertArrayEquals(new long[]{2, 4, 5, 3}, t.encode("unaffable!", 4), "truncated, keeps [CLS]/[SEP]");
    }

    @Test
    void matchesBertUncasedOnTheHuggingFaceDocExample() throws Exception {
        assumeTrue(Files.exists(MODEL.resolve("vocab.txt")), "model not downloaded (scripts/fetch-model.sh)");
        WordPieceTokenizer t = WordPieceTokenizer.load(MODEL.resolve("vocab.txt"));
        assertEquals(List.of("i", "have", "a", "new", "gp", "##u", "!"), t.tokenize("I have a new GPU!"));
    }

    @Test
    void embeddingsAreNormalisedAndSemanticallyOrdered() throws Exception {
        assumeTrue(Files.exists(MODEL.resolve("model.onnx")), "model not downloaded (scripts/fetch-model.sh)");
        try (OnnxEncoder encoder = new OnnxEncoder(MODEL)) {
            assertEquals(384, encoder.dimension());
            float[][] v = encoder.embed(List.of(
                    "A man is eating food.",
                    "A man is eating a piece of bread.",
                    "A man is riding a horse.",
                    "The stock market fell sharply today."));
            assertEquals(1.0, VectorMath.dot(v[0], v[0]), 1e-4);
            float bread = VectorMath.dot(v[0], v[1]);
            float horse = VectorMath.dot(v[0], v[2]);
            float stocks = VectorMath.dot(v[0], v[3]);
            assertTrue(bread > horse && horse > stocks, bread + " " + horse + " " + stocks);
            // batching must not change results (padding is masked out of the mean)
            assertArrayEquals(v[2], encoder.embed("A man is riding a horse."), 1e-4f);
        }
    }
}
