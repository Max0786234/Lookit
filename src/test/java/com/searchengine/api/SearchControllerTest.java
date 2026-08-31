package com.searchengine.api;

import com.searchengine.crawler.CrawlerService;
import com.searchengine.crawler.WebFetcherService;
import com.searchengine.document.Document;
import com.searchengine.query.SearchResult;
import com.searchengine.service.SearchEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    private CrawlerService crawlerService;

    @MockBean
    private WebFetcherService webFetcherService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchEndpointReturnsResults() throws Exception {
        when(searchEngineService.search("java search")).thenReturn(List.of(
                new SearchResult(new Document(1, "Java Search Engine", "local://java-search-engine",
                        "Java is a powerful language. This project builds a search engine using Java."), 0.95)
        ));

        mockMvc.perform(get("/api/search")
                        .param("q", "java search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("java search"))
                .andExpect(jsonPath("$.results[0].title").value("Java Search Engine"));
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
}
