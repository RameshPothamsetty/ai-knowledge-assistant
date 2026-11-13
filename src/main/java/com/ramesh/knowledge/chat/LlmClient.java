package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.search.ScoredChunk;

import java.util.List;
import java.util.function.Consumer;

/**
 * Abstraction over a text generation model. The default implementation works
 * offline; a hosted model can be plugged in without touching the RAG pipeline.
 */
public interface LlmClient {

    /**
     * Generates an answer for the given prompt.
     *
     * @param request system prompt, rendered user prompt, question and the
     *                retrieved chunks that grounded the prompt
     * @return the answer text, never blank
     */
    String complete(LlmRequest request);

    /**
     * Emits the answer as it is produced. The default splits the completed
     * answer into words, which gives a hosted implementation the same interface
     * without forcing every client to stream.
     */
    default void stream(LlmRequest request, Consumer<String> onToken) {
        String answer = complete(request);
        for (String piece : answer.split("(?<=\\s)")) {
            if (!piece.isEmpty()) {
                onToken.accept(piece);
            }
        }
    }

    String modelName();

    /**
     * Everything the model needs to answer, including the structured sources so
     * local implementations do not have to parse the rendered prompt.
     */
    record LlmRequest(String systemPrompt, String userPrompt, String question,
                      List<ScoredChunk> sources, AnswerStyle style) {

        public LlmRequest(String systemPrompt, String userPrompt, String question, List<ScoredChunk> sources) {
            this(systemPrompt, userPrompt, question, sources, AnswerStyle.DEFAULT);
        }

        public LlmRequest {
            sources = sources == null ? List.of() : List.copyOf(sources);
            style = style == null ? AnswerStyle.DEFAULT : style;
        }
    }
}
