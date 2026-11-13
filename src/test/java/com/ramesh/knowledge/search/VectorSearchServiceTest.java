package com.ramesh.knowledge.search;

import com.ramesh.knowledge.config.RagProperties;
import com.ramesh.knowledge.document.DocumentChunk;
import com.ramesh.knowledge.document.DocumentChunkRepository;
import com.ramesh.knowledge.document.KnowledgeDocument;
import com.ramesh.knowledge.embedding.EmbeddingCodec;
import com.ramesh.knowledge.embedding.HashingEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

    @Mock
    private DocumentChunkRepository chunkRepository;

    private VectorSearchService vectorSearchService;

    private final HashingEmbeddingService embeddingService = new HashingEmbeddingService(256);

    @BeforeEach
    void setUp() {
        vectorSearchService = serviceWithHybridWeight(0.75);
    }

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
    void searchesOnlyTheRequestedDocumentWhenAFilterIsGiven() {
        KnowledgeDocument selected = document(7L, "Java notes");
        KnowledgeDocument other = document(8L, "Cooking notes");
        DocumentChunk inScope = chunk(1L, selected, 0,
                "Java streams use filter and map to process collections.");
        DocumentChunk outOfScope = chunk(2L, other, 0,
                "Java is also the name of an island known for coffee.");
        when(chunkRepository.findAllWithDocumentByDocumentId(7L)).thenReturn(List.of(inScope));

        float[] query = embeddingService.embed("Java streams filter map");
        List<ScoredChunk> results = vectorSearchService.search(query, 5, 0.0, 7L);

        assertThat(results).extracting(ScoredChunk::documentId).containsOnly(7L);
        assertThat(results).extracting(ScoredChunk::chunkId).doesNotContain(outOfScope.getId());
        verify(chunkRepository).findAllWithDocumentByDocumentId(7L);
        verify(chunkRepository, never()).findAllWithDocument();
    }

    @Test
    void searchesTheWholeKnowledgeBaseWhenNoFilterIsGiven() {
        KnowledgeDocument document = document(4L, "Any notes");
        when(chunkRepository.findAllWithDocument()).thenReturn(List.of(
                chunk(1L, document, 0, "Retrieval reads every chunk when unfiltered.")));

        float[] query = embeddingService.embed("retrieval reads every chunk");
        List<ScoredChunk> results = vectorSearchService.search(query, 5, 0.0, null);

        assertThat(results).hasSize(1);
        verify(chunkRepository).findAllWithDocument();
        verify(chunkRepository, never()).findAllWithDocumentByDocumentId(anyLong());
    }

    @Test
    void returnsEmptyListForEmptyQueryWithoutReadingTheDatabase() {
        assertThat(vectorSearchService.search(new float[0], 5, 0.0)).isEmpty();
        assertThat(vectorSearchService.search(null, 5, 0.0)).isEmpty();
        assertThat(vectorSearchService.search(new float[]{1f}, 0, 0.0)).isEmpty();
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void withFullCosineWeightTheScoreIsTheCosineSimilarity() {
        VectorSearchService cosineOnly = serviceWithHybridWeight(1.0);
        KnowledgeDocument document = document(3L, "Notes");
        DocumentChunk only = chunk(1L, document, 0, "Tomatoes need sunlight and water.");
        when(chunkRepository.findAllWithDocument()).thenReturn(List.of(only));

        float[] query = embeddingService.embed("How much sunlight do tomatoes need?");
        double expected = VectorSearchService.cosineSimilarity(query, embeddingService.embed(only.getContent()));
        List<ScoredChunk> results = cosineOnly.search(
                "How much sunlight do tomatoes need?", query, 5, 0.0, null);

        // Stored vectors are rounded to five decimals, hence the loose tolerance.
        assertThat(results).singleElement().satisfies(result ->
                assertThat(result.score()).isCloseTo(expected, within(1e-4)));
    }

    @Test
    void withFullKeywordWeightTheBestKeywordMatchScoresOne() {
        VectorSearchService keywordOnly = serviceWithHybridWeight(0.0);
        KnowledgeDocument document = document(3L, "Notes");
        DocumentChunk matching = chunk(1L, document, 0,
                "Docker compose runs the application and a Postgres database.");
        DocumentChunk other = chunk(2L, document, 1,
                "Tomato seedlings need six hours of direct sunlight.");
        when(chunkRepository.findAllWithDocument()).thenReturn(List.of(matching, other));

        float[] query = embeddingService.embed("docker compose postgres database");
        List<ScoredChunk> results = keywordOnly.search(
                "docker compose postgres database", query, 5, 0.0, null);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).chunkId()).isEqualTo(1L);
        assertThat(results.get(0).score()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void decodesEachStoredVectorOnlyOnce() {
        VectorCache cache = new VectorCache(10);
        VectorSearchService cached = new VectorSearchService(chunkRepository, cache, properties(0.75));
        KnowledgeDocument document = document(3L, "Notes");
        DocumentChunk only = chunk(1L, document, 0, "Cached vectors are decoded once.");
        when(chunkRepository.findAllWithDocument()).thenReturn(List.of(only));

        float[] query = embeddingService.embed("cached vectors");
        cached.search(query, 5, 0.0);
        float[] first = cache.vector(only);
        float[] second = cache.vector(only);

        assertThat(cache.size()).isEqualTo(1);
        assertThat(second).isSameAs(first);
    }

    @Test
    void cosineSimilarityHandlesEdgeCases() {
        assertThat(VectorSearchService.cosineSimilarity(new float[]{1f, 0f}, new float[]{1f, 0f}))
                .isCloseTo(1.0, within(1e-9));
        assertThat(VectorSearchService.cosineSimilarity(new float[]{1f, 0f}, new float[]{0f, 1f}))
                .isCloseTo(0.0, within(1e-9));
        assertThat(VectorSearchService.cosineSimilarity(new float[]{1f}, new float[]{1f, 1f}))
                .isZero();
        assertThat(VectorSearchService.cosineSimilarity(new float[]{0f, 0f}, new float[]{1f, 1f}))
                .isZero();
    }

    private VectorSearchService serviceWithHybridWeight(double weight) {
        return new VectorSearchService(chunkRepository, new VectorCache(1024), properties(weight));
    }

    private RagProperties properties(double hybridWeight) {
        return new RagProperties(800, 120, 4, 20, 256, 0.05, 200_000, hybridWeight, 1024);
    }

    private KnowledgeDocument document(long id, String title) {
        KnowledgeDocument document = new KnowledgeDocument(title, "content", null, "text/plain", 7, Instant.now());
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
