package com.ramesh.knowledge.search;

import com.ramesh.knowledge.document.DocumentChunk;
import com.ramesh.knowledge.document.DocumentChunkRepository;
import com.ramesh.knowledge.embedding.EmbeddingCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * In-memory nearest neighbour search over stored chunk embeddings. Every chunk
 * is compared with the query vector using cosine similarity.
 *
 * <p>This is intentionally simple and database portable. The corpus for a
 * personal knowledge assistant is small (thousands of chunks), and the roadmap
 * tracks moving the scan into Postgres with pgvector once that stops being true.
 */
@Service
public class VectorSearchService {

    private final DocumentChunkRepository chunkRepository;

    public VectorSearchService(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Transactional(readOnly = true)
    public List<ScoredChunk> search(float[] queryVector, int topK, double minScore) {
        if (queryVector == null || queryVector.length == 0 || topK <= 0) {
            return List.of();
        }
        return chunkRepository.findAllWithDocument().stream()
                .map(chunk -> toScoredChunk(chunk, queryVector))
                .filter(scored -> scored.score() >= minScore)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .toList();
    }

    private ScoredChunk toScoredChunk(DocumentChunk chunk, float[] queryVector) {
        float[] chunkVector = EmbeddingCodec.decode(chunk.getEmbedding());
        double score = cosineSimilarity(queryVector, chunkVector);
        return new ScoredChunk(
                chunk.getId(),
                chunk.getDocument().getId(),
                chunk.getDocument().getTitle(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                score
        );
    }

    /**
     * Cosine similarity in {@code [-1, 1]}. Vectors of differing length (for
     * example after the embedding model changed) score zero rather than throwing.
     */
    public static double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0.0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
            leftNorm += (double) left[i] * left[i];
            rightNorm += (double) right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
