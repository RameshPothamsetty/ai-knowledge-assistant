package com.ramesh.knowledge.document;

/**
 * Raised for file extensions the ingestion pipeline has no parser for. Mapped
 * to {@code 415 Unsupported Media Type}.
 */
public class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException(String message) {
        super(message);
    }
}
