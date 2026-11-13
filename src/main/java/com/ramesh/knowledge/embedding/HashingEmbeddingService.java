package com.ramesh.knowledge.embedding;

import com.ramesh.knowledge.config.RagProperties;
import com.ramesh.knowledge.text.Terms;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        int terms = 0;
        for (String token : Terms.of(text)) {
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

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return "hashing-bow-v1/" + dimensions;
    }
}
