package com.ramesh.knowledge.web;

import com.ramesh.knowledge.config.RagProperties;
import com.ramesh.knowledge.embedding.EmbeddingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the active retrieval configuration. The UI uses it to show
 * the embedding model and to offer the same top-k range the server enforces,
 * instead of duplicating those numbers in JavaScript.
 */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final EmbeddingService embeddingService;
    private final RagProperties properties;

    public MetaController(EmbeddingService embeddingService, RagProperties properties) {
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    @GetMapping
    public MetaView get() {
        return new MetaView(
                embeddingService.modelName(),
                embeddingService.dimensions(),
                properties.chunkSize(),
                properties.chunkOverlap(),
                properties.defaultTopK(),
                properties.maxTopK(),
                properties.minScore()
        );
    }

    public record MetaView(
            String embeddingModel,
            int embeddingDimensions,
            int chunkSize,
            int chunkOverlap,
            int defaultTopK,
            int maxTopK,
            double minScore
    ) {
    }
}
