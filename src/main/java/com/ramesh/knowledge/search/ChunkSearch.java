package com.ramesh.knowledge.search;

import java.util.List;

/**
 * Retrieval over stored chunk vectors. Two implementations exist: the default
 * in-memory scan, which reads every chunk and is fine for thousands of chunks,
 * and a pgvector-backed search that runs inside Postgres when the
 * {@code postgres} profile is active.
 */
public interface ChunkSearch {

    /**
     * @param question   the user question, used for the keyword part of the score
     * @param queryVector the embedded question
     * @param topK        how many chunks to return at most
     * @param minScore    cosine similarity below which a chunk is irrelevant
     * @param documentId  when set, restrict the search to one document
     */
    List<ScoredChunk> search(String question, float[] queryVector, int topK,
                             double minScore, Long documentId);
}
