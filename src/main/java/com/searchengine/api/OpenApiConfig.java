package com.searchengine.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API metadata for the generated OpenAPI document (/v3/api-docs, UI at /swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI lookitOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Lookit search API")
                .version("1.0")
                .description("Keyword (BM25F + WAND), semantic (MiniLM + HNSW) and hybrid (RRF) search, "
                        + "autocomplete, spelling suggestions, documents and asynchronous polite crawling.")
                .license(new License().name("MIT")));
    }
}
