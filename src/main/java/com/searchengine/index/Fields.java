package com.searchengine.index;

import com.searchengine.document.Document;

import java.util.List;

/** Indexed field names and how each is read from a {@link Document}. */
public final class Fields {
    public static final String TITLE = "title";
    public static final String BODY = "body";
    public static final List<String> ALL = List.of(TITLE, BODY);

    private Fields() {
    }

    public static String text(Document document, String field) {
        return switch (field) {
            case TITLE -> document.title();
            case BODY -> document.content();
            default -> throw new IllegalArgumentException("Unknown field: " + field);
        };
    }
}
