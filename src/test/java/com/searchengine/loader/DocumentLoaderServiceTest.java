package com.searchengine.loader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentLoaderServiceTest {

    @Test
    void markdownIsReducedToPlainText() {
        String md = "# Title\n\n## Query **Caching**\n- item `code`\n```java\nint x;\n```\nSee [docs](https://x.y).";
        assertEquals("Title\n\nQuery Caching\nitem code\n\nint x;\n\nSee docs.", DocumentLoaderService.stripMarkdown(md));
    }
}
