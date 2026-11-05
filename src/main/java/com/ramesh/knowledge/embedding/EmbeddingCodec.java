package com.ramesh.knowledge.embedding;

import java.util.Locale;

/**
 * Encodes and decodes embedding vectors to the compact JSON array stored in the
 * database, for example {@code [0.12345,-0.54321,...]}.
 *
 * <p>Values are rounded to five decimals: retrieval quality is unaffected while
 * rows stay small enough for an in-memory scan.
 */
public final class EmbeddingCodec {

    private EmbeddingCodec() {
    }

    public static String encode(float[] vector) {
        StringBuilder json = new StringBuilder(vector.length * 9);
        json.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(String.format(Locale.ROOT, "%.5f", vector[i]));
        }
        return json.append(']').toString();
    }

    public static float[] decode(String json) {
        if (json == null) {
            throw new IllegalArgumentException("embedding payload must not be null");
        }
        String body = json.trim();
        if (body.length() < 2 || body.charAt(0) != '[' || body.charAt(body.length() - 1) != ']') {
            throw new IllegalArgumentException("embedding payload is not a JSON array");
        }
        String inner = body.substring(1, body.length() - 1).trim();
        if (inner.isEmpty()) {
            return new float[0];
        }
        String[] parts = inner.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }
}
