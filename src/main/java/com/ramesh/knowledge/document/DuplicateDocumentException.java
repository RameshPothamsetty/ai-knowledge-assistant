package com.ramesh.knowledge.document;

/**
 * Raised when a document with the same title and content is already in the
 * knowledge base. Re-ingesting it would only add duplicate chunks that compete
 * with the original in retrieval.
 */
public class DuplicateDocumentException extends RuntimeException {

    public DuplicateDocumentException(String title) {
        super("a document titled \"" + title + "\" with identical content already exists");
    }
}
