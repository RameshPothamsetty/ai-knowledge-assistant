package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.chat.LlmClient.LlmRequest;
import com.ramesh.knowledge.chat.dto.AnswerResponse;
import com.ramesh.knowledge.chat.dto.SourceSnippet;
import com.ramesh.knowledge.config.RagProperties;
import com.ramesh.knowledge.embedding.EmbeddingService;
import com.ramesh.knowledge.search.ScoredChunk;
import com.ramesh.knowledge.search.VectorSearchService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates retrieval augmented generation:
 * <ol>
 *   <li>embed the question with the same model used for chunks,</li>
 *   <li>retrieve the closest chunks above the relevance threshold,</li>
 *   <li>render a grounded prompt and ask the model for an answer,</li>
 *   <li>return the answer together with its sources.</li>
 * </ol>
 * When nothing relevant is found the model is not called, which avoids
 * hallucinated answers on an empty knowledge base.
 */
@Service
public class RagService {

    static final String NO_CONTEXT_ANSWER =
            "I could not find anything relevant in the uploaded documents. "
                    + "Upload a document that covers this topic or rephrase the question.";

    private static final int SNIPPET_LENGTH = 320;

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final LlmClient llmClient;
    private final RagPromptBuilder promptBuilder;
    private final RagProperties properties;

    public RagService(EmbeddingService embeddingService,
                      VectorSearchService vectorSearchService,
                      LlmClient llmClient,
                      RagPromptBuilder promptBuilder,
                      RagProperties properties) {
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
    }

    public AnswerResponse ask(String question, Integer requestedTopK) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        long startedAt = System.nanoTime();

        float[] questionVector = embeddingService.embed(question);
        List<ScoredChunk> hits = vectorSearchService.search(
                questionVector, resolveTopK(requestedTopK), properties.minScore());

        List<SourceSnippet> sources = hits.stream()
                .map(hit -> new SourceSnippet(
                        hit.documentId(),
                        hit.documentTitle(),
                        hit.chunkIndex(),
                        Math.round(hit.score() * 1000.0) / 1000.0,
                        snippet(hit.content())))
                .toList();

        String answer = hits.isEmpty()
                ? NO_CONTEXT_ANSWER
                : llmClient.complete(new LlmRequest(
                        promptBuilder.systemPrompt(),
                        promptBuilder.userPrompt(question, hits),
                        question,
                        hits));

        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new AnswerResponse(answer, llmClient.modelName(), sources, latencyMs);
    }

    /**
     * Applies the configured default and hard maximum so a client cannot make
     * the server scan or prompt an unbounded number of chunks.
     */
    private int resolveTopK(Integer requestedTopK) {
        int topK = requestedTopK == null ? properties.defaultTopK() : requestedTopK;
        return Math.max(1, Math.min(topK, properties.maxTopK()));
    }

    private String snippet(String content) {
        String cleaned = content.trim();
        if (cleaned.length() <= SNIPPET_LENGTH) {
            return cleaned;
        }
        int cut = cleaned.lastIndexOf(' ', SNIPPET_LENGTH);
        if (cut < SNIPPET_LENGTH / 2) {
            cut = SNIPPET_LENGTH;
        }
        return cleaned.substring(0, cut).trim() + "...";
    }
}
