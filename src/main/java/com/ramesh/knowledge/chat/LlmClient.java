package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.search.ScoredChunk;

import java.util.List;

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

    String modelName();

    /**
     * Everything the model needs to answer, including the structured sources so
     * local implementations do not have to parse the rendered prompt.
     */
    record LlmRequest(String systemPrompt, String userPrompt, String question, List<ScoredChunk> sources) {
        public LlmRequest {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }
}
