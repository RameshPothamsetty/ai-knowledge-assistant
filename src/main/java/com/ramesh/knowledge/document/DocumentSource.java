package com.ramesh.knowledge.document;

import java.nio.charset.StandardCharsets;

/**
 * Where the text of a document came from. Pasted text has no filename, and an
 * upload keeps its original name, MIME type and byte size so the API can report
 * the source without guessing.
 *
 * @param filename    original filename, or {@code null} for pasted text
 * @param contentType MIME type of the source
 * @param sizeBytes   size of the source, in bytes
 */
public record DocumentSource(String filename, String contentType, long sizeBytes) {

    public static DocumentSource pasted(String content) {
        return new DocumentSource(null, "text/plain", content.getBytes(StandardCharsets.UTF_8).length);
    }
}
