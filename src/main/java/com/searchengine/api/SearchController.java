package com.searchengine.api;

import com.searchengine.api.ApiExceptionHandler.NotFoundException;
import com.searchengine.api.dto.CrawlRequestDto;
import com.searchengine.api.dto.CrawlResponseDto;
import com.searchengine.api.dto.CreateDocumentRequest;
import com.searchengine.api.dto.DocumentDto;
import com.searchengine.api.dto.SearchResponseDto;
import com.searchengine.api.dto.SearchResultDto;
import com.searchengine.api.dto.StatsDto;
import com.searchengine.crawler.WebFetcherService;
import com.searchengine.document.Document;
import com.searchengine.service.SearchEngineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class SearchController {

    private final SearchEngineService searchEngineService;
    private final WebFetcherService webFetcherService;

    public SearchController(SearchEngineService searchEngineService, WebFetcherService webFetcherService) {
        this.searchEngineService = searchEngineService;
        this.webFetcherService = webFetcherService;
    }

    @GetMapping("/search")
    public SearchResponseDto search(@RequestParam @NotBlank @Size(max = 512) String q,
                                    @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
                                    @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        SearchEngineService.SearchPage result = searchEngineService.search(q, page, size);
        List<SearchResultDto> items = result.hits().stream()
                .map(hit -> new SearchResultDto(
                        hit.document().id(),
                        hit.document().title(),
                        hit.document().url(),
                        hit.snippet().text(),
                        hit.snippet().highlights().stream().map(h -> new int[]{h.start(), h.end()}).toList(),
                        hit.score()))
                .toList();
        return new SearchResponseDto(q, result.total(), page, size, result.tookMs(), items);
    }

    @GetMapping("/documents")
    public List<DocumentDto> getAllDocuments() {
        return searchEngineService.getAllDocuments().stream().map(SearchController::toDto).toList();
    }

    @GetMapping("/documents/{id}")
    public DocumentDto getDocument(@PathVariable int id) {
        return searchEngineService.getDocument(id)
                .map(SearchController::toDto)
                .orElseThrow(() -> new NotFoundException("Document not found: " + id));
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto createDocument(@Valid @RequestBody CreateDocumentRequest request) {
        return toDto(searchEngineService.addDocument(request.title(), request.url(), request.content()));
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable int id) {
        if (!searchEngineService.deleteDocument(id)) {
            throw new NotFoundException("Document not found: " + id);
        }
    }

    @GetMapping("/stats")
    public StatsDto getStats() {
        return new StatsDto(
                searchEngineService.documentCount(),
                searchEngineService.totalTermOccurrences(),
                searchEngineService.vocabularySize());
    }

    @PostMapping("/crawl")
    public CrawlResponseDto crawl(@Valid @RequestBody CrawlRequestDto request) {
        WebFetcherService.CrawlStats stats =
                webFetcherService.crawl(request.seedUrl(), request.maxDepth(), request.maxPages());
        searchEngineService.reindexAllDocuments();
        return new CrawlResponseDto(
                stats.seedUrl(),
                stats.pagesAdded(),
                List.of("Crawled " + stats.domain() + ", found " + stats.pagesAdded()
                        + " documents, attempted " + stats.attemptedPages()));
    }

    private static DocumentDto toDto(Document document) {
        return new DocumentDto(document.id(), document.title(), document.url(), document.content());
    }
}
