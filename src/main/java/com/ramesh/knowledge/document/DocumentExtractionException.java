package com.ramesh.knowledge.document;

/**
 * Raised when an uploaded file cannot be turned into text: a corrupt PDF, a
 * password-protected document, or a scan without a text layer. Mapped to
 * {@code 422 Unprocessable Entity} because the request is well formed but its
 * content cannot be processed.
 */
public class DocumentExtractionException extends RuntimeException {

    public DocumentExtractionException(String message) {
        super(message);
    }

    public DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
