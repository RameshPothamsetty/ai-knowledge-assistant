package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.chat.LlmClient.LlmRequest;
import com.ramesh.knowledge.chat.dto.AnswerResponse;
import com.ramesh.knowledge.config.RagProperties;
import com.ramesh.knowledge.embedding.EmbeddingService;
import com.ramesh.knowledge.search.ScoredChunk;
import com.ramesh.knowledge.search.VectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorSearchService vectorSearchService;

    @Mock
    private LlmClient llmClient;

    private final RagProperties properties = new RagProperties(800, 120, 4, 20, 256, 0.05);

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(
                embeddingService, vectorSearchService, llmClient, new RagPromptBuilder(), properties);
    }

    @Test
    void answersFromRetrievedChunksAndReportsSources() {
        float[] vector = {0.6f, 0.8f};
        ScoredChunk hit = new ScoredChunk(10L, 3L, "Spring notes", 1,
                "Spring Boot exposes health checks through the Actuator module.", 0.8123);
        when(embeddingService.embed("How do I check health?")).thenReturn(vector);
        when(vectorSearchService.search(same(vector), eq(4), eq(0.05))).thenReturn(List.of(hit));
        when(llmClient.complete(any())).thenReturn("Use Actuator [1].");
        when(llmClient.modelName()).thenReturn("test-model");

        AnswerResponse response = ragService.ask("How do I check health?", null);

        assertThat(response.answer()).isEqualTo("Use Actuator [1].");
        assertThat(response.model()).isEqualTo("test-model");
        assertThat(response.latencyMs()).isNotNegative();
        assertThat(response.sources()).singleElement().satisfies(source -> {
            assertThat(source.documentId()).isEqualTo(3L);
            assertThat(source.documentTitle()).isEqualTo("Spring notes");
            assertThat(source.chunkIndex()).isEqualTo(1);
            assertThat(source.score()).isEqualTo(0.812);
            assertThat(source.snippet()).contains("Actuator");
        });

        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).complete(requestCaptor.capture());
        LlmRequest request = requestCaptor.getValue();
        assertThat(request.question()).isEqualTo("How do I check health?");
        assertThat(request.systemPrompt()).contains("only the numbered context passages");
        assertThat(request.userPrompt())
                .contains("[1] Spring notes (chunk 2)")
                .contains("Actuator")
                .contains("Question: How do I check health?");
    }

    @Test
    void skipsTheModelWhenNothingIsRelevant() {
        when(embeddingService.embed("unrelated question")).thenReturn(new float[]{1f, 0f});
        when(vectorSearchService.search(any(), anyInt(), anyDouble())).thenReturn(List.of());

        AnswerResponse response = ragService.ask("unrelated question", null);

        assertThat(response.answer()).isEqualTo(RagService.NO_CONTEXT_ANSWER);
        assertThat(response.sources()).isEmpty();
        verify(llmClient, never()).complete(any());
    }

    @Test
    void clampsRequestedTopKToTheConfiguredMaximum() {
        when(embeddingService.embed(anyString())).thenReturn(new float[]{1f});
        when(vectorSearchService.search(any(), anyInt(), anyDouble())).thenReturn(List.of());

        ragService.ask("question", 500);
        verify(vectorSearchService).search(any(), eq(20), anyDouble());

        ragService.ask("question", 0);
        verify(vectorSearchService).search(any(), eq(1), anyDouble());
    }

    @Test
    void rejectsBlankQuestions() {
        assertThatThrownBy(() -> ragService.ask("  ", null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(embeddingService, vectorSearchService, llmClient);
    }

    @Test
    void truncatesLongSnippets() {
        String longContent = "word ".repeat(200).trim();
        ScoredChunk hit = new ScoredChunk(1L, 1L, "Doc", 0, longContent, 0.5);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{1f});
        when(vectorSearchService.search(any(), anyInt(), anyDouble())).thenReturn(List.of(hit));
        when(llmClient.complete(any())).thenReturn("answer");
        when(llmClient.modelName()).thenReturn("test-model");

        AnswerResponse response = ragService.ask("question", 2);

        assertThat(response.sources().get(0).snippet()).endsWith("...").hasSizeLessThan(330);
    }
}
