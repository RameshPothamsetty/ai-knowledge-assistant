package com.ramesh.knowledge.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitFeedbackRequest(
        @NotBlank(message = "answerId is required")
        @Size(max = 64, message = "answerId must be at most 64 characters")
        String answerId,

        boolean helpful,

        @Size(max = 500, message = "comment must be at most 500 characters")
        String comment
) {
}
