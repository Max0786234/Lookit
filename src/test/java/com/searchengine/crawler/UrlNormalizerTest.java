package com.searchengine.crawler;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlNormalizerTest {

    @Test
    void normalizesSchemeHostPortAndFragment() {
        assertEquals(Optional.of("https://example.com/a/c?x=1"),
                UrlNormalizer.normalize("HTTPS://Example.COM:443/a/b/../c?x=1#top"));
        assertEquals(Optional.of("https://example.com/"), UrlNormalizer.normalize("example.com"));
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertTrue(UrlNormalizer.normalize("mailto:a@b.com").isEmpty());
        assertTrue(UrlNormalizer.normalize("javascript:void(0)").isEmpty());
    }

    @Test
    void sameSiteRespectsDotBoundary() {
        assertTrue(UrlNormalizer.isSameSite("example.com", "example.com"));
        assertTrue(UrlNormalizer.isSameSite("docs.example.com", "example.com"));
        assertFalse(UrlNormalizer.isSameSite("evilexample.com", "example.com"));
    }

    @Test
    void decodesGzipBodies() throws Exception {
        byte[] plain = "<html>hello</html>".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(plain);
        }
        assertArrayEquals(plain, HttpFetcher.decode(bos.toByteArray(), "gzip"));
        assertArrayEquals(plain, HttpFetcher.decode(plain, ""));
    }
}
