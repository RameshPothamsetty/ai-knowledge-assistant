package com.ramesh.knowledge.embedding;

/**
 * Turns text into a vector that can be compared with cosine similarity.
 * Keeping this behind an interface lets the project start with a dependency
 * free local implementation and later swap in a model provider.
 */
public interface EmbeddingService {

    float[] embed(String text);

    int dimensions();

    String modelName();
}
