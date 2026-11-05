package com.ramesh.knowledge.chat.dto;

/**
 * A retrieved passage that supported the answer, trimmed for display.
 */
public record SourceSnippet(
        Long documentId,
        String documentTitle,
        int chunkIndex,
        double score,
        String snippet
) {
}
