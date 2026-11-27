package com.searchengine.api;

import com.searchengine.service.CrawlJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CrawlController.class)
@org.springframework.context.annotation.Import(CorsConfig.class)
class CrawlControllerTest {

    @MockBean
    private CrawlJobService jobs;

    @Autowired
    private MockMvc mockMvc;

    private static CrawlJobService.Job job(String seed) throws Exception {
        Constructor<CrawlJobService.Job> c = CrawlJobService.Job.class.getDeclaredConstructor(String.class);
        c.setAccessible(true);
        return c.newInstance(seed);
    }

    @Test
    void startReturns202WithJobIdAndPollingReturnsStatus() throws Exception {
        CrawlJobService.Job job = job("https://example.com/");
        when(jobs.start(eq("https://example.com"), eq(2), eq(1), eq(true))).thenReturn(job);
        when(jobs.get(job.id)).thenReturn(Optional.of(job));

        mockMvc.perform(post("/api/crawl").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seedUrl\":\"https://example.com\",\"maxPages\":2,\"maxDepth\":1}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(job.id))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(get("/api/crawl/" + job.id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seedUrl").value("https://example.com/"));
    }

    @Test
    void postFromTheDockerUiOriginIsAllowedAndUnknownOriginsAreRejected() throws Exception {
        CrawlJobService.Job job = job("https://example.com/");
        when(jobs.start(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(job);
        String body = "{\"seedUrl\":\"https://example.com\",\"maxPages\":1,\"maxDepth\":0}";
        mockMvc.perform(post("/api/crawl").header("Origin", "http://localhost:8081")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/crawl").header("Origin", "http://127.0.0.1:8081")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/crawl").header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRequestsAndUnknownJobs() throws Exception {
        mockMvc.perform(post("/api/crawl").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seedUrl\":\"https://example.com\",\"maxPages\":5000,\"maxDepth\":1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/crawl").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seedUrl\":\"not a url\",\"maxPages\":5,\"maxDepth\":1}"))
                .andExpect(status().isBadRequest());
        when(jobs.get(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/crawl/nope")).andExpect(status().isNotFound());
        when(jobs.start(anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Invalid seed URL"));
        mockMvc.perform(post("/api/crawl").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seedUrl\":\"ftp-ish\",\"maxPages\":5,\"maxDepth\":1}"))
                .andExpect(status().isBadRequest());
    }
}
