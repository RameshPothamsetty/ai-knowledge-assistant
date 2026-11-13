package com.ramesh.knowledge.search;

import com.ramesh.knowledge.config.RagProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Retrieval backed by pgvector. The nearest candidates are chosen by Postgres
 * through the HNSW index, then re-ranked in Java with the same cosine/BM25
 * blend the in-memory implementation uses, so scores keep their meaning when
 * the profile changes.
 *
 * <p>Only a candidate pool is fetched (a few times {@code topK}) rather than
 * the whole corpus: the index decides which chunks are worth scoring, and the
 * keyword part reorders that pool. This is an approximation of the in-memory
 * blend, and the trade-off is deliberate — it is what makes the query cost
 * independent of the number of stored chunks.
 *
 * <p>Active only under the {@code postgres} profile; H2 keeps using
 * {@link VectorSearchService}.
 */
@Service
@Primary
@Profile("postgres")
public class PgVectorSearchService implements ChunkSearch {

    /** Candidates fetched per requested result, so BM25 can reorder sensibly. */
    private static final int CANDIDATE_MULTIPLIER = 4;

    private static final int MIN_CANDIDATES = 50;

    private static final String BASE_SQL = """
            select c.id, c.document_id, d.title, c.chunk_index, c.content,
                   1 - (c.embedding_vector <=> cast(? as vector)) as cosine
            from document_chunk c
            join knowledge_document d on d.id = c.document_id
            where c.embedding_vector is not null
              and 1 - (c.embedding_vector <=> cast(? as vector)) >= ?
            """;

    private static final String ORDER_AND_LIMIT = """
            order by c.embedding_vector <=> cast(? as vector)
            limit ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final double hybridWeight;

    public PgVectorSearchService(JdbcTemplate jdbcTemplate, RagProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.hybridWeight = Math.max(0.0, Math.min(1.0, properties.hybridWeight()));
    }

    @Override
    public List<ScoredChunk> search(String question, float[] queryVector, int topK,
                                     double minScore, Long documentId) {
        if (queryVector == null || queryVector.length == 0 || topK <= 0) {
            return List.of();
        }
        String vector = toVectorLiteral(queryVector);
        int candidateLimit = Math.max(MIN_CANDIDATES, topK * CANDIDATE_MULTIPLIER);

        List<Candidate> candidates = documentId == null
                ? jdbcTemplate.query(BASE_SQL + ORDER_AND_LIMIT, this::mapCandidate,
                        vector, vector, minScore, vector, candidateLimit)
                : jdbcTemplate.query(BASE_SQL + """
                          and c.document_id = ?
                        """ + ORDER_AND_LIMIT, this::mapCandidate,
                        vector, vector, minScore, documentId, vector, candidateLimit);

        if (candidates.isEmpty()) {
            return List.of();
        }

        double[] keywordScores = Bm25Scorer.score(question,
                candidates.stream().map(Candidate::content).toList());

        List<ScoredChunk> ranked = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            double blended = hybridWeight * candidate.cosine() + (1 - hybridWeight) * keywordScores[i];
            ranked.add(new ScoredChunk(
                    candidate.chunkId(),
                    candidate.documentId(),
                    candidate.documentTitle(),
                    candidate.chunkIndex(),
                    candidate.content(),
                    blended
            ));
        }
        return ranked.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .toList();
    }

    private Candidate mapCandidate(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
        return new Candidate(
                rs.getLong("id"),
                rs.getLong("document_id"),
                rs.getString("title"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("cosine")
        );
    }

    /**
     * pgvector's text input format: {@code [0.1,0.2]}.
     */
    static String toVectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 12 + 2);
        literal.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(vector[i]);
        }
        return literal.append(']').toString();
    }

    private record Candidate(Long chunkId, Long documentId, String documentTitle,
                             int chunkIndex, String content, double cosine) {
    }
}
