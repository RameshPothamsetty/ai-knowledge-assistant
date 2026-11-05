package com.ramesh.knowledge.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a document from raw text.
 */
public record CreateDocumentRequest(
        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @NotBlank(message = "content is required")
        @Size(max = 200_000, message = "content must be at most 200000 characters")
        String content,

        @Size(max = 255, message = "sourceName must be at most 255 characters")
        String sourceName
) {
}
