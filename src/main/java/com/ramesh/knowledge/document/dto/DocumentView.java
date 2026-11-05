package com.ramesh.knowledge.document.dto;

import com.ramesh.knowledge.document.KnowledgeDocument;

import java.time.Instant;

/**
 * Public representation of a stored document; the raw content is omitted
 * because it can be large and is not needed by the UI.
 */
public record DocumentView(
        Long id,
        String title,
        String sourceName,
        int chunkCount,
        Instant createdAt
) {
    public static DocumentView from(KnowledgeDocument document) {
        return new DocumentView(
                document.getId(),
                document.getTitle(),
                document.getSourceName(),
                document.getChunkCount(),
                document.getCreatedAt()
        );
    }
}
