package com.searchengine.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Extracts title, main text and outgoing links from HTML. Stateless and thread-safe. */
public final class HtmlExtractor {
    /** Hard cap on extracted text so a pathological page cannot blow up the index. */
    static final int MAX_TEXT_CHARS = 500_000;

    public record Page(String title, String text, List<String> links, boolean noindex, boolean nofollow) {
    }

    public Page extract(String url, String html) {
        Document doc = Jsoup.parse(html, url);
        String robots = doc.select("meta[name=robots]").attr("content").toLowerCase(java.util.Locale.ROOT);
        boolean noindex = robots.contains("noindex");
        boolean nofollow = robots.contains("nofollow");
        List<String> links = nofollow ? List.of() : extractLinks(doc);
        doc.select("script, style, noscript, template, svg, nav, aside, footer, header, form, "
                + ".navbox, .infobox, .reflist, .sidebar, .navigation, .footer, [role=navigation]").remove();
        return new Page(extractTitle(doc, url), extractText(doc), links, noindex, nofollow);
    }

    private static String extractTitle(Document doc, String url) {
        for (String candidate : new String[]{
                doc.title(),
                doc.select("meta[property=og:title]").attr("content"),
                textOf(doc.selectFirst("h1"))}) {
            if (candidate != null && !candidate.isBlank()) {
                return truncate(candidate.replaceAll("\\s+", " ").trim(), 500);
            }
        }
        try {
            String path = URI.create(url).getPath();
            String last = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
            String title = last.replaceAll("[_-]+", " ").replaceAll("\\.\\w+$", "").trim();
            return title.isEmpty() ? URI.create(url).getHost() : title;
        } catch (Exception e) {
            return "Untitled";
        }
    }

    private static String textOf(Element e) {
        return e == null ? null : e.text();
    }

    private static String extractText(Document doc) {
        Element main = doc.selectFirst("main, article, .mw-parser-output, [role=main], #content, .content");
        String raw = main != null ? main.text() : doc.body() == null ? "" : doc.body().text();
        String cleaned = raw.replaceAll("\\[\\d+]", "")       // citation markers [1]
                .replaceAll("\\[\\s*edit\\s*]", "")           // wiki edit links
                .replaceAll("\\s+", " ")
                .trim();
        return truncate(cleaned, MAX_TEXT_CHARS);
    }

    private static List<String> extractLinks(Document doc) {
        Set<String> links = new LinkedHashSet<>();
        for (Element a : doc.select("a[href]")) {
            if (a.attr("rel").toLowerCase(java.util.Locale.ROOT).contains("nofollow")) {
                continue;
            }
            String href = a.attr("abs:href");
            if (!href.isBlank()) {
                UrlNormalizer.normalize(href).ifPresent(links::add);
            }
        }
        return new ArrayList<>(links);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
