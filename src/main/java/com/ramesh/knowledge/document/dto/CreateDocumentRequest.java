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

        // The size limit is enforced by the ingestion service, which also guards
        // text extracted from uploads; that keeps one limit and one 413 response.
        @NotBlank(message = "content is required")
        String content,

        @Size(max = 255, message = "sourceName must be at most 255 characters")
        String sourceName
) {
}
