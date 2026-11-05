package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.chat.LlmClient.LlmRequest;
import com.ramesh.knowledge.search.ScoredChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractiveLlmClientTest {

    private final ExtractiveLlmClient client = new ExtractiveLlmClient();

    @Test
    void answersWithTheMostRelevantSentenceAndACitation() {
        ScoredChunk source = new ScoredChunk(1L, 2L, "Spring notes", 0,
                "Spring Boot starts an embedded Tomcat server by default. "
                        + "It also provides auto configuration for common libraries.",
                0.8);

        String answer = client.complete(new LlmRequest("system", "prompt",
                "When does Spring Boot start Tomcat?", List.of(source)));

        assertThat(answer).contains("embedded Tomcat").contains("[1]");
        assertThat(client.modelName()).isEqualTo("extractive-fallback-v1");
    }

    @Test
    void ignoresSentencesThatShareNoWordsWithTheQuestion() {
        ScoredChunk source = new ScoredChunk(1L, 2L, "Doc", 0,
                "Bread needs yeast and warm water. Vector search compares embeddings with cosine similarity.",
                0.7);

        String answer = client.complete(new LlmRequest("system", "prompt",
                "How does vector search compare embeddings?", List.of(source)));

        assertThat(answer).contains("cosine similarity");
        assertThat(answer).doesNotContain("yeast");
        assertThat(answer).containsOnlyOnce("[1]");
    }

    @Test
    void admitsWhenThereAreNoSources() {
        String answer = client.complete(new LlmRequest("system", "prompt", "question", List.of()));

        assertThat(answer).contains("could not find anything relevant");
    }

    @Test
    void isDeterministicForTheSameInput() {
        ScoredChunk source = new ScoredChunk(1L, 2L, "Doc", 0,
                "Vector search compares embeddings with cosine similarity.", 0.7);
        LlmRequest request = new LlmRequest("system", "prompt",
                "How does vector search compare embeddings?", List.of(source));

        assertThat(client.complete(request)).isEqualTo(client.complete(request));
    }
}
