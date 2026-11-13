package com.ramesh.knowledge.document;

/**
 * Raised when a document's text exceeds the configured limit. Mapped to
 * {@code 413 Payload Too Large}, which tells the caller to split the input
 * rather than fix its syntax.
 */
public class DocumentTooLargeException extends RuntimeException {

    public DocumentTooLargeException(int characters, int limit) {
        super("document has " + characters + " characters, the limit is " + limit);
    }
}
