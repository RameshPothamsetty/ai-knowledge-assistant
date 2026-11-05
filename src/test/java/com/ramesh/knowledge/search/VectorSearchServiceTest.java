package com.ramesh.knowledge.search;

import com.ramesh.knowledge.document.DocumentChunk;
import com.ramesh.knowledge.document.DocumentChunkRepository;
import com.ramesh.knowledge.document.KnowledgeDocument;
import com.ramesh.knowledge.embedding.EmbeddingCodec;
import com.ramesh.knowledge.embedding.HashingEmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

    @Mock
    private DocumentChunkRepository chunkRepository;

    @InjectMocks
    private VectorSearchService vectorSearchService;

    private final HashingEmbeddingService embeddingService = new HashingEmbeddingService(256);

    @Test
    void ranksChunksByCosineSimilarityAndRespectsTopK() {
        KnowledgeDocument document = document(7L, "Java notes");
        DocumentChunk relevant = chunk(1L, document, 0,
                "Java streams use filter and map operations to process collections.");
        DocumentChunk alsoRelevant = chunk(2L, document, 1,
                "Collect results from a stream with collect after map and filter.");
        DocumentChunk irrelevant = chunk(3L, document, 2,
                "Baking sourdough bread needs yeast, water and a warm kitchen.");
        when(chunkRepository.findAllWithDocument()).thenReturn(List.of(relevant, alsoRelevant, irrelevant));

        float[] query = embeddingService.embed("Java streams filter map collect");
        List<ScoredChunk> results = vectorSearchService.search(query, 2, 0.1);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ScoredChunk::chunkId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(results).extracting(ScoredChunk::documentTitle).containsOnly("Java notes");
        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(results.get(1).score());
        assertThat(results).noneMatch(result -> result.content().contains("sourdough"));
    }

    @Test
    void filtersOutChunksBelowTheScoreThreshold() {
        KnowledgeDocument document = document(1L, "Cooking");
        when(chunkRepository.findAllWithDocument()).thenReturn(List.of(
                chunk(1L, document, 0, "Sourdough bread needs yeast and warm water.")));

        float[] query = embeddingService.embed("vector database indexing");

        assertThat(vectorSearchService.search(query, 5, 0.5)).isEmpty();
    }

    @Test
    void returnsEmptyListForEmptyQueryWithoutReadingTheDatabase() {
        assertThat(vectorSearchService.search(new float[0], 5, 0.0)).isEmpty();
        assertThat(vectorSearchService.search(null, 5, 0.0)).isEmpty();
        assertThat(vectorSearchService.search(new float[]{1f}, 0, 0.0)).isEmpty();
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void cosineSimilarityHandlesEdgeCases() {
        assertThat(VectorSearchService.cosineSimilarity(new float[]{1f, 0f}, new float[]{1f, 0f}))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(VectorSearchService.cosineSimilarity(new float[]{1f, 0f}, new float[]{0f, 1f}))
                .isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(VectorSearchService.cosineSimilarity(new float[]{1f}, new float[]{1f, 1f}))
                .isZero();
        assertThat(VectorSearchService.cosineSimilarity(new float[]{0f, 0f}, new float[]{1f, 1f}))
                .isZero();
    }

    private KnowledgeDocument document(long id, String title) {
        KnowledgeDocument document = new KnowledgeDocument(title, "content", null, Instant.now());
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private DocumentChunk chunk(long id, KnowledgeDocument document, int index, String content) {
        DocumentChunk chunk = new DocumentChunk(
                document, index, content, EmbeddingCodec.encode(embeddingService.embed(content)), 10);
        ReflectionTestUtils.setField(chunk, "id", id);
        return chunk;
    }
}
