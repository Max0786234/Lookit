package com.searchengine.api;

import com.searchengine.api.ApiExceptionHandler.NotFoundException;
import com.searchengine.api.dto.CrawlRequestDto;
import com.searchengine.api.dto.CrawlResponseDto;
import com.searchengine.service.CrawlJobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Asynchronous crawl jobs: start one, poll its status, list or cancel. */
@RestController
@RequestMapping("/api/crawl")
public class CrawlController {
    private final CrawlJobService jobs;

    public CrawlController(CrawlJobService jobs) {
        this.jobs = jobs;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CrawlResponseDto start(@Valid @RequestBody CrawlRequestDto request) {
        boolean sameSite = request.sameSiteOnly() == null || request.sameSiteOnly();
        return toDto(jobs.start(request.seedUrl(), request.maxPages(), request.maxDepth(), sameSite));
    }

    @GetMapping("/{id}")
    public CrawlResponseDto status(@PathVariable String id) {
        return jobs.get(id).map(CrawlController::toDto).orElseThrow(() -> new NotFoundException("No crawl job " + id));
    }

    @GetMapping
    public List<CrawlResponseDto> list() {
        return jobs.list().stream().map(CrawlController::toDto).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CrawlResponseDto cancel(@PathVariable String id) {
        CrawlJobService.Job job = jobs.get(id).orElseThrow(() -> new NotFoundException("No crawl job " + id));
        jobs.cancel(id);
        return toDto(job);
    }

    static CrawlResponseDto toDto(CrawlJobService.Job j) {
        return new CrawlResponseDto(j.id, j.seedUrl, j.status().name(), j.stats.fetched.get(), j.stats.stored.get(),
                j.stats.duplicates.get(), j.stats.robotsBlocked.get(), j.stats.errors.get(), j.stats.queued.get(),
                j.startedAt, j.finishedAt(), j.error());
    }
}
