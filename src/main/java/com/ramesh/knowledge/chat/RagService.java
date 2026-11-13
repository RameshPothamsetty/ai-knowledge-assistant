package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.chat.LlmClient.LlmRequest;
import com.ramesh.knowledge.chat.dto.AnswerResponse;
import com.ramesh.knowledge.chat.dto.SourceSnippet;
import com.ramesh.knowledge.config.RagProperties;
import com.ramesh.knowledge.embedding.EmbeddingService;
import com.ramesh.knowledge.search.ScoredChunk;
import com.ramesh.knowledge.search.VectorSearchService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Orchestrates retrieval augmented generation:
 * <ol>
 *   <li>embed the question with the same model used for chunks,</li>
 *   <li>retrieve the closest chunks above the relevance threshold, optionally
 *       narrowed to a single document,</li>
 *   <li>render a grounded prompt — with style instructions and the previous
 *       turns of the conversation — and ask the model for an answer,</li>
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
    private final ConversationStore conversationStore;
    private final MeterRegistry meterRegistry;

    public RagService(EmbeddingService embeddingService,
                      VectorSearchService vectorSearchService,
                      LlmClient llmClient,
                      RagPromptBuilder promptBuilder,
                      RagProperties properties,
                      ConversationStore conversationStore,
                      MeterRegistry meterRegistry) {
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
        this.conversationStore = conversationStore;
        this.meterRegistry = meterRegistry;
    }

    public AnswerResponse ask(String question, Integer requestedTopK, Long documentId) {
        return ask(question, requestedTopK, documentId, AnswerStyle.DEFAULT, null);
    }

    /**
     * @param style          requested answer length and tone; {@code null} means default
     * @param conversationId optional id used to carry earlier turns into the prompt
     * @param documentId     optional filter restricting retrieval to one document
     */
    public AnswerResponse ask(String question, Integer requestedTopK, Long documentId,
                              AnswerStyle style, String conversationId) {
        requireQuestion(question);
        long startedAt = System.nanoTime();

        AnswerStyle answerStyle = style == null ? AnswerStyle.DEFAULT : style;
        List<ConversationStore.Turn> history = conversationStore.history(conversationId);
        Timer.Sample retrieval = Timer.start(meterRegistry);
        List<ScoredChunk> hits = retrieve(question, requestedTopK, documentId);
        retrieval.stop(meterRegistry.timer("rag.retrieval"));
        List<SourceSnippet> sources = toSources(hits);

        Timer.Sample generation = Timer.start(meterRegistry);
        String answer = hits.isEmpty()
                ? NO_CONTEXT_ANSWER
                : llmClient.complete(request(question, hits, answerStyle, history));
        generation.stop(meterRegistry.timer("rag.generation"));
        meterRegistry.counter("rag.answers", "outcome", hits.isEmpty() ? "no-context" : "answered")
                .increment();

        conversationStore.record(conversationId, question, answer);
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new AnswerResponse(UUID.randomUUID().toString(), answer,
                llmClient.modelName(), sources, latencyMs);
    }

    /**
     * Streams the answer token by token. Sources are handed to {@code onSources}
     * before generation starts, because retrieval is already finished, and the
     * full answer is still recorded so a later turn can refer to it.
     */
    public StreamedAnswer askStreaming(String question, Integer requestedTopK, Long documentId,
                                       AnswerStyle style, String conversationId,
                                       Consumer<List<SourceSnippet>> onSources,
                                       Consumer<String> onToken) {
        requireQuestion(question);
        long startedAt = System.nanoTime();

        AnswerStyle answerStyle = style == null ? AnswerStyle.DEFAULT : style;
        List<ConversationStore.Turn> history = conversationStore.history(conversationId);
        Timer.Sample retrieval = Timer.start(meterRegistry);
        List<ScoredChunk> hits = retrieve(question, requestedTopK, documentId);
        retrieval.stop(meterRegistry.timer("rag.retrieval"));
        List<SourceSnippet> sources = toSources(hits);
        onSources.accept(sources);

        Timer.Sample generation = Timer.start(meterRegistry);
        StringBuilder answer = new StringBuilder();
        Consumer<String> collecting = token -> {
            answer.append(token);
            onToken.accept(token);
        };
        if (hits.isEmpty()) {
            collecting.accept(NO_CONTEXT_ANSWER);
        } else {
            llmClient.stream(request(question, hits, answerStyle, history), collecting);
        }

        generation.stop(meterRegistry.timer("rag.generation"));
        meterRegistry.counter("rag.answers", "outcome", hits.isEmpty() ? "no-context" : "answered")
                .increment();

        conversationStore.record(conversationId, question, answer.toString());
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new StreamedAnswer(UUID.randomUUID().toString(), llmClient.modelName(), latencyMs);
    }

    private LlmRequest request(String question, List<ScoredChunk> hits,
                               AnswerStyle style, List<ConversationStore.Turn> history) {
        return new LlmRequest(
                promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(question, hits, style, history),
                question,
                hits,
                style);
    }

    private List<ScoredChunk> retrieve(String question, Integer requestedTopK, Long documentId) {
        float[] questionVector = embeddingService.embed(question);
        return vectorSearchService.search(
                question, questionVector, resolveTopK(requestedTopK), properties.minScore(), documentId);
    }

    private List<SourceSnippet> toSources(List<ScoredChunk> hits) {
        return hits.stream()
                .map(hit -> new SourceSnippet(
                        hit.documentId(),
                        hit.documentTitle(),
                        hit.chunkIndex(),
                        Math.round(hit.score() * 1000.0) / 1000.0,
                        snippet(hit.content())))
                .toList();
    }

    private void requireQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
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

    /**
     * What the caller needs once the stream ends: the model that produced the
     * answer and how long it took. Sources arrive before the first token through
     * the {@code onSources} callback.
     */
    public record StreamedAnswer(String answerId, String model, long latencyMs) {
    }
}
