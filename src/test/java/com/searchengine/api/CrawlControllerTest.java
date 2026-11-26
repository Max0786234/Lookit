package com.searchengine.api;

import com.searchengine.crawler.CrawlerService;
import com.searchengine.crawler.WebFetcherService;
import com.searchengine.service.SearchEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class CrawlControllerTest {

    @MockBean
    private SearchEngineService searchEngineService;

    @MockBean
    private CrawlerService crawlerService;

    @MockBean
    private WebFetcherService webFetcherService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void crawlEndpointReturnsSeedAndVisitedPages() throws Exception {
        when(webFetcherService.crawl(anyString(), anyInt(), anyInt())).thenReturn(
                new WebFetcherService.CrawlStats("https://example.com", 1, 1, 1, "example.com")
        );

        mockMvc.perform(post("/api/crawl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seedUrl\":\"https://example.com\",\"maxPages\":2,\"maxDepth\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seedUrl").value("https://example.com"));
    }
}
