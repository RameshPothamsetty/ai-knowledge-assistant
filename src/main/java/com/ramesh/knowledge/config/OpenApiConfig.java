package com.ramesh.knowledge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata for the generated OpenAPI document. The paths and schemas come from
 * the controllers and DTOs, so this only names and describes the API.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI knowledgeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AI Knowledge Assistant API")
                .version("0.1.0")
                .description("""
                        Upload documents, then ask questions grounded in them.
                        Answers carry the passages they were built from, and every \
                        failure uses the same JSON error body.
                        """)
                .license(new License().name("MIT")));
    }
}
