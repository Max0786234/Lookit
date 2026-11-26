package com.searchengine.api;

import com.searchengine.api.dto.CrawlRequestDto;
import com.searchengine.api.dto.CrawlResponseDto;
import com.searchengine.api.dto.CreateDocumentRequest;
import com.searchengine.api.dto.DocumentDto;
import com.searchengine.api.dto.SearchResponseDto;
import com.searchengine.api.dto.SearchResultDto;
import com.searchengine.api.dto.StatsDto;
import com.searchengine.crawler.CrawlerService;
import com.searchengine.crawler.WebFetcherService;
import com.searchengine.document.Document;
import com.searchengine.query.SearchResult;
import com.searchengine.service.SearchEngineService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class SearchController {

    private final SearchEngineService searchEngineService;
    private final CrawlerService crawlerService;
    private final WebFetcherService webFetcherService;

    public SearchController(SearchEngineService searchEngineService, CrawlerService crawlerService, WebFetcherService webFetcherService) {
        this.searchEngineService = searchEngineService;
        this.crawlerService = crawlerService;
        this.webFetcherService = webFetcherService;
    }

    @GetMapping("/search")
    public SearchResponseDto search(@RequestParam String q) {
        List<SearchResult> results = searchEngineService.search(q);

        List<SearchResultDto> items = results.stream()
                .map(result -> new SearchResultDto(
                        result.document().title(),
                        result.document().url(),
                        result.document().content(),
                        result.score()
                ))
                .collect(Collectors.toList());

        return new SearchResponseDto(q, items);
    }

    @GetMapping("/documents")
    public List<DocumentDto> getAllDocuments() {
        return searchEngineService.getAllDocuments().stream()
                .map(document -> new DocumentDto(document.id(), document.title(), document.url(), document.content()))
                .collect(Collectors.toList());
    }

    @GetMapping("/documents/{id}")
    public DocumentDto getDocument(@PathVariable int id) {
        Document document = searchEngineService.getDocument(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        return new DocumentDto(document.id(), document.title(), document.url(), document.content());
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto createDocument(@RequestBody CreateDocumentRequest request) {
        Document document = searchEngineService.addDocument(request.title(), request.url(), request.content());
        return new DocumentDto(document.id(), document.title(), document.url(), document.content());
    }

    @GetMapping("/stats")
    public StatsDto getStats() {
        return new StatsDto(
                searchEngineService.documentCount(),
                searchEngineService.totalTermOccurrences(),
                searchEngineService.vocabularySize()
        );
    }

    @PostMapping("/crawl")
    @ResponseStatus(HttpStatus.OK)
    public CrawlResponseDto crawl(@RequestBody CrawlRequestDto request) {
        String seedUrl = request.seedUrl();
        int maxPages = Math.max(1, Math.min(request.maxPages(), 100)); // Cap at 100 pages
        int maxDepth = Math.max(0, Math.min(request.maxDepth(), 3)); // Cap at depth 3

        // Crawl and store documents in database
        WebFetcherService.CrawlStats stats = webFetcherService.crawl(seedUrl, maxDepth, maxPages);
        
        // Reload search index with newly added documents from database
        searchEngineService.reindexAllDocuments();

        return new CrawlResponseDto(
                stats.seedUrl(),
                stats.pagesAdded(),
                java.util.List.of("Crawled " + stats.domain() + ", found " + stats.pagesAdded() + " documents, attempted " + stats.attemptedPages())
        );
    }
}
