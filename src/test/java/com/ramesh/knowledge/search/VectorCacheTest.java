package com.ramesh.knowledge.search;

import com.ramesh.knowledge.document.DocumentChunk;
import com.ramesh.knowledge.document.KnowledgeDocument;
import com.ramesh.knowledge.embedding.EmbeddingCodec;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VectorCacheTest {

    @Test
    void returnsTheSameInstanceOnACacheHit() {
        VectorCache cache = new VectorCache(10);
        DocumentChunk chunk = chunk(1L, 5L, new float[]{0.25f, -0.5f});

        assertThat(cache.vector(chunk)).isSameAs(cache.vector(chunk));
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void evictsOnlyTheGivenDocumentsVectors() {
        VectorCache cache = new VectorCache(10);
        DocumentChunk first = chunk(1L, 5L, new float[]{1f});
        DocumentChunk second = chunk(2L, 6L, new float[]{1f});
        cache.vector(first);
        cache.vector(second);

        cache.evictDocument(5L);

        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.vector(second)).isSameAs(cache.vector(second));
    }

    @Test
    void dropsTheMapWhenItReachesTheCapacity() {
        VectorCache cache = new VectorCache(2);
        cache.vector(chunk(1L, 5L, new float[]{1f}));
        cache.vector(chunk(2L, 5L, new float[]{1f}));
        cache.vector(chunk(3L, 5L, new float[]{1f}));

        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.vector(chunk(1L, 5L, new float[]{1f}))).hasSize(1);
    }

    @Test
    void clearRemovesEverything() {
        VectorCache cache = new VectorCache(10);
        cache.vector(chunk(1L, 5L, new float[]{1f}));
        cache.clear();

        assertThat(cache.size()).isZero();
    }

    private DocumentChunk chunk(long id, long documentId, float[] vector) {
        KnowledgeDocument document = new KnowledgeDocument("Doc", "content", null, "text/plain", 7, Instant.now());
        ReflectionTestUtils.setField(document, "id", documentId);
        DocumentChunk chunk = new DocumentChunk(document, 0, "content", EmbeddingCodec.encode(vector), 1);
        ReflectionTestUtils.setField(chunk, "id", id);
        return chunk;
    }
}
