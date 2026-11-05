package com.ramesh.knowledge.search;

/**
 * A chunk returned by retrieval together with its similarity score.
 */
public record ScoredChunk(
        Long chunkId,
        Long documentId,
        String documentTitle,
        int chunkIndex,
        String content,
        double score
) {
}
