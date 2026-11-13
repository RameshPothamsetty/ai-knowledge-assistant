package com.ramesh.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable RAG settings, bound from the {@code app.rag.*} configuration keys.
 *
 * @param chunkSize           maximum characters per chunk
 * @param chunkOverlap        characters of the previous chunk repeated at the start of the next one
 * @param defaultTopK         number of chunks retrieved when the caller does not ask for a size
 * @param maxTopK             upper bound for the requested top-k value
 * @param embeddingDimensions size of the local hashing embedding vectors
 * @param minScore            cosine similarity below which a chunk is considered irrelevant
 * @param maxDocumentCharacters largest amount of text a single document may contain
 * @param hybridWeight        share of the cosine score in the ranking blend; the rest is BM25
 * @param vectorCacheSize     maximum number of decoded chunk vectors kept in memory
 */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        int chunkSize,
        int chunkOverlap,
        int defaultTopK,
        int maxTopK,
        int embeddingDimensions,
        double minScore,
        int maxDocumentCharacters,
        double hybridWeight,
        int vectorCacheSize
) {
}
