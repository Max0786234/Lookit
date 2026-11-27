package com.searchengine.service;

import com.searchengine.search.SearchEngine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Micrometer instrumentation, exported at {@code /actuator/prometheus}:
 * {@code lookit_search_seconds{mode}} (latency histogram), {@code lookit_search_zero_results_total},
 * and gauges for documents and vocabulary size.
 */
@Component
public class SearchMetrics {
    private final MeterRegistry registry;
    private final Counter zeroResults;

    public SearchMetrics(MeterRegistry registry, SearchEngineService service) {
        this.registry = registry;
        this.zeroResults = Counter.builder("lookit.search.zero.results")
                .description("Searches that returned no hits").register(registry);
        Gauge.builder("lookit.index.documents", service, SearchEngineService::documentCount)
                .description("Documents in the search index").register(registry);
        Gauge.builder("lookit.index.vocabulary", service, SearchEngineService::vocabularySize)
                .description("Distinct body terms in the index").register(registry);
    }

    public SearchEngine.Results timeSearch(SearchEngine.Mode mode, Supplier<SearchEngine.Results> search) {
        Timer timer = Timer.builder("lookit.search")
                .description("Search latency")
                .tag("mode", mode.name().toLowerCase(Locale.ROOT))
                .publishPercentileHistogram()
                .register(registry);
        SearchEngine.Results results = timer.record(search);
        if (results != null && results.total() == 0) {
            zeroResults.increment();
        }
        return results;
    }
}
