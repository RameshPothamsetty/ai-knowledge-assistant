package com.ramesh.knowledge.chat.dto;

import com.ramesh.knowledge.chat.AnswerLength;
import com.ramesh.knowledge.chat.AnswerTone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A question for the knowledge base.
 *
 * <p>Every field except {@code question} is optional. {@code topK} is clamped by
 * the server to the configured maximum, {@code documentId} restricts retrieval to
 * a single document, {@code length} and {@code tone} shape the answer, and
 * {@code conversationId} ties follow-up questions to earlier turns.
 */
public record AskRequest(
        @NotBlank(message = "question is required")
        @Size(max = 1000, message = "question must be at most 1000 characters")
        String question,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 20, message = "topK must be at most 20")
        Integer topK,

        @Positive(message = "documentId must be positive")
        Long documentId,

        AnswerLength length,

        AnswerTone tone,

        @Size(max = 64, message = "conversationId must be at most 64 characters")
        String conversationId
) {
}
