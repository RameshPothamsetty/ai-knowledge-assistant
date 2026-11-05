package com.ramesh.knowledge.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A question for the knowledge base. {@code topK} is optional and clamped by
 * the server to the configured maximum.
 */
public record AskRequest(
        @NotBlank(message = "question is required")
        @Size(max = 1000, message = "question must be at most 1000 characters")
        String question,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 20, message = "topK must be at most 20")
        Integer topK
) {
}
