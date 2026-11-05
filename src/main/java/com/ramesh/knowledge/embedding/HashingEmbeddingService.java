package com.ramesh.knowledge.embedding;

import com.ramesh.knowledge.config.RagProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependency-free embedding model based on feature hashing ("the hashing
 * trick"). Each word is hashed into one of N dimensions with a signing bit, and
 * the resulting term-frequency vector is L2 normalised.
 *
 * <p>It is intentionally not a neural embedding: it keeps the project runnable
 * offline and in CI without API keys, gives deterministic results for tests, and
 * still captures lexical overlap well enough to demonstrate the full RAG
 * pipeline. Documents and questions are embedded with the same model, so
 * retrieval is meaningful.
 */
@Service
public class HashingEmbeddingService implements EmbeddingService {

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has", "have",
            "in", "is", "it", "its", "of", "on", "or", "that", "the", "this", "to", "was",
            "were", "will", "with", "what", "which", "who", "how", "when", "where", "why"
    );

    private final int dimensions;

    @Autowired
    public HashingEmbeddingService(RagProperties properties) {
        this(properties.embeddingDimensions());
    }

    public HashingEmbeddingService(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("embedding dimensions must be positive");
        }
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimensions];
        if (text == null || text.isBlank()) {
            return vector;
        }

        Matcher matcher = TOKEN.matcher(text.toLowerCase());
        int terms = 0;
        while (matcher.find()) {
            String token = stem(matcher.group());
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            int hash = token.hashCode();
            int index = Math.floorMod(hash, dimensions);
            float sign = ((hash >>> 16) & 1) == 0 ? 1f : -1f;
            vector[index] += sign;
            terms++;
        }

        if (terms == 0) {
            return vector;
        }

        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            return vector;
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }

    /**
     * Very small suffix stripping so "documents" and "document" share a token.
     */
    private String stem(String token) {
        if (token.length() > 4 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 4 && (token.endsWith("ses") || token.endsWith("xes"))) {
            return token.substring(0, token.length() - 2);
        }
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return "hashing-bow-v1/" + dimensions;
    }
}
