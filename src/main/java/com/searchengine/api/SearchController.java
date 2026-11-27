package com.searchengine.api;

import com.searchengine.api.ApiExceptionHandler.NotFoundException;
import com.searchengine.api.dto.CreateDocumentRequest;
import com.searchengine.api.dto.DocumentDto;
import com.searchengine.api.dto.SearchResponseDto;
import com.searchengine.api.dto.SearchResultDto;
import com.searchengine.api.dto.StatsDto;
import com.searchengine.api.dto.SuggestResponseDto;
import com.searchengine.document.Document;
import com.searchengine.search.SearchEngine;
import com.searchengine.service.SearchEngineService;
import com.searchengine.service.SearchMetrics;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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
public class SearchController {

    private final SearchEngineService searchEngineService;
    private final SearchMetrics metrics;

    public SearchController(SearchEngineService searchEngineService, SearchMetrics metrics) {
        this.searchEngineService = searchEngineService;
        this.metrics = metrics;
    }

    @GetMapping("/search")
    public SearchResponseDto search(@RequestParam @NotBlank @Size(max = 512) String q,
                                    @RequestParam(defaultValue = "1") @Min(1) @Max(100) int page,
                                    @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
                                    @RequestParam(defaultValue = "keyword") String mode) {
        SearchEngine.Mode m = parseMode(mode);
        SearchEngine.Results result = metrics.timeSearch(m, () -> searchEngineService.search(q, page, size, m));
        List<SearchResultDto> items = result.hits().stream()
                .map(hit -> new SearchResultDto(
                        hit.document().id(),
                        hit.document().title(),
                        hit.document().url(),
                        hit.snippet().text(),
                        hit.snippet().highlights().stream().map(h -> new int[]{h.start(), h.end()}).toList(),
                        hit.score()))
                .toList();
        return new SearchResponseDto(q, m.name().toLowerCase(java.util.Locale.ROOT), result.total(),
                result.totalIsLowerBound(), page, size, result.tookMs(), result.didYouMean(), items);
    }

    private static SearchEngine.Mode parseMode(String mode) {
        try {
            return SearchEngine.Mode.valueOf(mode.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("mode must be keyword, semantic or hybrid");
        }
    }

    /** Autocomplete: completions of the last word of {@code q}. */
    @GetMapping("/suggest")
    public SuggestResponseDto suggest(@RequestParam @Size(max = 200) String q,
                                      @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        return new SuggestResponseDto(q, searchEngineService.suggest(q, limit));
    }

    @GetMapping("/documents")
    public List<DocumentDto> listDocuments(@RequestParam(defaultValue = "1") @Min(1) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return searchEngineService.listDocuments(page, size).stream().map(SearchController::toDto).toList();
    }

    /** Example searches built from the titles of indexed documents, for the home screen. */
    @GetMapping("/examples")
    public List<String> examples(@RequestParam(defaultValue = "6") @Min(1) @Max(20) int count) {
        return searchEngineService.exampleQueries(count);
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
        List<StatsDto.Source> sources = searchEngineService.sources().entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new StatsDto.Source(e.getKey(), e.getValue()))
                .toList();
        return new StatsDto(searchEngineService.documentCount(), searchEngineService.totalTermOccurrences(),
                searchEngineService.vocabularySize(), sources, searchEngineService.semanticEnabled());
    }

    private static DocumentDto toDto(Document document) {
        return new DocumentDto(document.id(), document.title(), document.url(), document.content());
    }
}
