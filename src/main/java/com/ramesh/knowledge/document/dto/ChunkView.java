package com.ramesh.knowledge.document.dto;

import com.ramesh.knowledge.document.DocumentChunk;

/**
 * One stored chunk as shown in the UI's chunk preview. The embedding is left
 * out: it is large, internal and meaningless to a reader.
 */
public record ChunkView(
        Long id,
        int chunkIndex,
        String content,
        int tokenCount
) {
    public static ChunkView from(DocumentChunk chunk) {
        return new ChunkView(
                chunk.getId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getTokenCount()
        );
    }
}
