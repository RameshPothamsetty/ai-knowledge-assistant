package com.ramesh.knowledge.chat.dto;

import java.util.List;

/**
 * Answer returned by the RAG pipeline, including the sources it was grounded on
 * so the user can verify the claim instead of trusting it.
 */
public record AnswerResponse(
        String answer,
        String model,
        List<SourceSnippet> sources,
        long latencyMs
) {
    public AnswerResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
