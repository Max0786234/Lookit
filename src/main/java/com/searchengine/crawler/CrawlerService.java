package com.searchengine.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CrawlerService {
    
    public CrawledPage crawlPage(String url, String html) {
        try {
            Document doc = Jsoup.parse(html, url);
            
            // Remove script and style tags which clutter the content
            doc.select("script, style, nav, noscript, .navbox, .infobox, .reflist").remove();
            
            // Extract title
            String title = extractTitle(doc, url);
            
            // Extract clean text content
            String text = extractText(doc);
            
            // Extract links
            List<String> links = extractLinks(doc);
            
            return new CrawledPage(title, text, links);
        } catch (Exception e) {
            System.err.println("Error parsing HTML from " + url + ": " + e.getMessage());
            return new CrawledPage("", "", new ArrayList<>());
        }
    }

    private String extractTitle(Document doc, String url) {
        // Try multiple title sources
        
        // 1. First try <title> tag
        Element titleTag = doc.selectFirst("title");
        if (titleTag != null && !titleTag.text().isBlank()) {
            return titleTag.text().replaceAll("\\s+", " ").trim();
        }
        
        // 2. Try h1 tag (common for main heading)
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text().replaceAll("\\s+", " ").trim();
        }
        
        // 3. Try meta og:title (Open Graph)
        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null && ogTitle.hasAttr("content")) {
            return ogTitle.attr("content").replaceAll("\\s+", " ").trim();
        }
        
        // 4. Fallback to URL
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            return path.substring(path.lastIndexOf('/') + 1)
                    .replaceAll("[_-]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
        } catch (Exception e) {
            return "Untitled";
        }
    }

    private String extractText(Document doc) {
        // Remove common navigation/sidebar content
        doc.select("nav, aside, .sidebar, .navigation, footer, .footer").remove();
        
        // Get main content
        Element main = doc.selectFirst("main, article, .mw-parser-output, [role=main], .content, #content");
        if (main != null) {
            return cleanText(main.text());
        }
        
        // Fallback to body
        return cleanText(doc.body().text());
    }

    private String cleanText(String text) {
        // Remove extra whitespace and normalize
        String cleaned = text.replaceAll("\\s+", " ")
                            .replaceAll("\\[[0-9]+\\]", "") // Remove citation markers [1], [2], etc
                            .replaceAll("\\(\\s*edit\\s*\\)", "") // Remove Wikipedia edit markers
                            .trim();
        
        // Limit to first 5000 chars to avoid huge content
        if (cleaned.length() > 5000) {
            cleaned = cleaned.substring(0, 5000) + "...";
        }
        
        return cleaned;
    }

    private List<String> extractLinks(Document doc) {
        Set<String> uniqueLinks = new HashSet<>();
        Elements links = doc.select("a[href]");
        
        for (Element link : links) {
            String href = link.attr("abs:href"); // Get absolute URL
            if (href != null && !href.isBlank() && !href.startsWith("#")) {
                uniqueLinks.add(href);
            }
        }
        
        return new ArrayList<>(uniqueLinks);
    }

    public record CrawledPage(String title, String text, List<String> links) {
    }
}
