package com.ramesh.knowledge.embedding;

import com.ramesh.knowledge.search.VectorSearchService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingEmbeddingServiceTest {

    private final HashingEmbeddingService embeddingService = new HashingEmbeddingService(256);

    @Test
    void producesDeterministicVectorsOfTheConfiguredSize() {
        float[] first = embeddingService.embed("Spring Boot auto configuration");
        float[] second = embeddingService.embed("Spring Boot auto configuration");

        assertThat(first).hasSize(256);
        assertThat(second).containsExactly(first);
        assertThat(embeddingService.modelName()).isEqualTo("hashing-bow-v1/256");
    }

    @Test
    void producesUnitLengthVectorsForNonEmptyText() {
        float[] vector = embeddingService.embed("vector search with cosine similarity");

        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void ranksRelatedTextAboveUnrelatedText() {
        float[] question = embeddingService.embed("How does Spring Boot auto configuration work?");
        float[] related = embeddingService.embed(
                "Spring Boot auto configuration configures beans based on the classpath.");
        float[] unrelated = embeddingService.embed(
                "Chocolate cake needs butter, sugar, eggs and flour baked for forty minutes.");

        double relatedScore = VectorSearchService.cosineSimilarity(question, related);
        double unrelatedScore = VectorSearchService.cosineSimilarity(question, unrelated);

        assertThat(relatedScore).isGreaterThan(unrelatedScore);
        assertThat(relatedScore).isGreaterThan(0.1);
    }

    @Test
    void returnsZeroVectorForStopWordsOnly() {
        float[] vector = embeddingService.embed("the and of to");

        assertThat(vector).containsOnly(0f);
    }

    @Test
    void handlesEmptyInput() {
        assertThat(embeddingService.embed("")).containsOnly(0f);
        assertThat(embeddingService.embed(null)).containsOnly(0f);
    }
}
