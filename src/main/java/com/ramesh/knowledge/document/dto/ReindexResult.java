package com.ramesh.knowledge.document.dto;

/**
 * What a reindex rebuilt. Documents keep their identity, so links and ids
 * stored elsewhere stay valid while every chunk is regenerated.
 */
public record ReindexResult(
        long documents,
        long chunks,
        long durationMs
) {
}
