package com.searchengine.api;

import com.searchengine.document.Document;
import com.searchengine.search.SearchEngine;
import com.searchengine.search.SnippetGenerator;
import com.searchengine.service.SearchEngineService;
import com.searchengine.service.SearchMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @MockBean
    private SearchEngineService searchEngineService;

    @MockBean
    private SearchMetrics metrics;

    @Autowired
    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void passThroughMetrics() {
        when(metrics.timeSearch(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());
    }

    @Test
    void searchEndpointReturnsResults() throws Exception {
        when(searchEngineService.search("java search", 1, 10, SearchEngine.Mode.KEYWORD)).thenReturn(new SearchEngine.Results(1, false, List.of(
                new SearchEngine.Hit(new Document(1, "Java Search Engine", "local://java-search-engine",
                        "Java is a powerful language."), 0.95,
                        new SnippetGenerator.Snippet("Java is a powerful language.",
                                List.of(new SnippetGenerator.Highlight(0, 4))))), 3, "java searches"));

        mockMvc.perform(get("/api/search")
                        .param("q", "java search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("java search"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.mode").value("keyword"))
                .andExpect(jsonPath("$.didYouMean").value("java searches"))
                .andExpect(jsonPath("$.results[0].highlights[0][1]").value(4))
                .andExpect(jsonPath("$.results[0].title").value("Java Search Engine"));
    }

    @Test
    void suggestEndpointAndInvalidMode() throws Exception {
        when(searchEngineService.suggest("retr", 8)).thenReturn(List.of("retrieval", "retrieve"));
        mockMvc.perform(get("/api/suggest").param("q", "retr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0]").value("retrieval"));
        mockMvc.perform(get("/api/search").param("q", "x").param("mode", "telepathic"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsListSourcesAndExamplesComeFromTheCorpus() throws Exception {
        when(searchEngineService.sources()).thenReturn(java.util.Map.of("SciFact scientific abstracts", 5183L, "Sample articles", 6L));
        when(searchEngineService.semanticEnabled()).thenReturn(true);
        when(searchEngineService.exampleQueries(6)).thenReturn(List.of("Vitamin D and bone density"));
        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].name").value("SciFact scientific abstracts"))
                .andExpect(jsonPath("$.sources[0].documents").value(5183))
                .andExpect(jsonPath("$.semanticEnabled").value(true));
        mockMvc.perform(get("/api/examples"))
                .andExpect(jsonPath("$[0]").value("Vitamin D and bone density"));
    }

    @Test
    void documentEndpointReturnsDocumentDto() throws Exception {
        when(searchEngineService.getDocument(1)).thenReturn(Optional.of(
                new Document(1, "Java Search Engine", "local://java-search-engine",
                        "Java is a powerful language. This project builds a search engine using Java."))
        );

        mockMvc.perform(get("/api/documents/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Java Search Engine"));
    }

    @Test
    void postDocumentCreatesNewDocument() throws Exception {
        when(searchEngineService.addDocument(anyString(), anyString(), anyString()))
                .thenReturn(new Document(99, "New Java Article", "local://new-java-article",
                        "Java article about search and ranking."));

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Java Article\",\"url\":\"local://new-java-article\",\"content\":\"Java article about search and ranking.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Java Article"))
                .andExpect(jsonPath("$.url").value("local://new-java-article"));
    }

    @Test
    void missingDocumentReturns404Problem() throws Exception {
        when(searchEngineService.getDocument(42)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/documents/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Document not found: 42"));
    }

    @Test
    void blankQueryReturns400() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "   "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidDocumentBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"url\":\"local://x\",\"content\":\"text\"}"))
                .andExpect(status().isBadRequest());
    }
}
