package com.ramesh.knowledge.search;

import com.ramesh.knowledge.config.RagProperties;
import com.ramesh.knowledge.document.DocumentChunk;
import com.ramesh.knowledge.document.DocumentChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * In-memory nearest neighbour search over stored chunk embeddings. Every chunk
 * is compared with the query vector using cosine similarity and, when the
 * question text is available, ranked by a blend of cosine and a BM25 keyword
 * score.
 *
 * <p>This is intentionally simple and database portable, and it stays the
 * default on H2. When the {@code postgres} profile is active,
 * {@link PgVectorSearchService} replaces it with an index-backed query.
 */
@Service
public class VectorSearchService implements ChunkSearch {

    private final DocumentChunkRepository chunkRepository;
    private final VectorCache vectorCache;
    private final double hybridWeight;

    public VectorSearchService(DocumentChunkRepository chunkRepository,
                               VectorCache vectorCache,
                               RagProperties properties) {
        this.chunkRepository = chunkRepository;
        this.vectorCache = vectorCache;
        this.hybridWeight = Math.max(0.0, Math.min(1.0, properties.hybridWeight()));
    }

    @Transactional(readOnly = true)
    public List<ScoredChunk> search(float[] queryVector, int topK, double minScore) {
        return search(null, queryVector, topK, minScore, null);
    }

    @Transactional(readOnly = true)
    public List<ScoredChunk> search(float[] queryVector, int topK, double minScore, Long documentId) {
        return search(null, queryVector, topK, minScore, documentId);
    }

    /**
     * @param question   the user question, used for the keyword score; when
     *                   {@code null} only cosine similarity is ranked
     * @param documentId when set, only chunks of that document are considered;
     *                   {@code null} searches the whole knowledge base
     */
    @Override
    @Transactional(readOnly = true)
    public List<ScoredChunk> search(String question, float[] queryVector, int topK,
                                    double minScore, Long documentId) {
        if (queryVector == null || queryVector.length == 0 || topK <= 0) {
            return List.of();
        }
        List<DocumentChunk> candidates = documentId == null
                ? chunkRepository.findAllWithDocument()
                : chunkRepository.findAllWithDocumentByDocumentId(documentId);
        if (candidates.isEmpty()) {
            return List.of();
        }

        double[] keywordScores = Bm25Scorer.score(question,
                candidates.stream().map(DocumentChunk::getContent).toList());

        List<ScoredChunk> ranked = new java.util.ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            DocumentChunk chunk = candidates.get(i);
            double cosine = cosineSimilarity(queryVector, vectorCache.vector(chunk));
            // Cosine remains the relevance gate: the keyword blend changes the
            // order of relevant chunks, not which chunks count as relevant.
            if (cosine < minScore) {
                continue;
            }
            double blended = hybridWeight * cosine + (1 - hybridWeight) * keywordScores[i];
            ranked.add(new ScoredChunk(
                    chunk.getId(),
                    chunk.getDocument().getId(),
                    chunk.getDocument().getTitle(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    blended
            ));
        }
        return ranked.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .toList();
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
