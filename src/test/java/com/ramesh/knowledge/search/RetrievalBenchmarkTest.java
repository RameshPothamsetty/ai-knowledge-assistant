package com.ramesh.knowledge.search;

import com.ramesh.knowledge.embedding.EmbeddingCodec;
import com.ramesh.knowledge.embedding.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seeds a corpus and times the two retrieval paths against the same data: the
 * in-memory scan used on H2 and the HNSW-indexed query used on Postgres.
 *
 * <p>This is a benchmark, not a regression gate: the numbers depend on the
 * machine, so the test prints them instead of asserting a ratio. The useful
 * reading is the shape — the in-memory scan grows with the corpus, while the
 * index-backed query stays roughly flat once HNSW has enough vectors to do
 * better than a sequential scan.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Testcontainers(disabledWithoutDocker = true)
class RetrievalBenchmarkTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private static final int DOCUMENTS = 200;
    private static final int CHUNKS_PER_DOCUMENT = 20;
    private static final int ITERATIONS = 10;
    private static final int WARMUP = 2;
    private static final int TOP_K = 5;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChunkSearch chunkSearch;

    @Autowired
    private VectorSearchService inMemorySearch;

    @Autowired
    private PgVectorBootstrap pgVectorBootstrap;

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void comparesTheInMemoryScanWithTheHnswIndex() {
        assertThat(chunkSearch).isInstanceOf(PgVectorSearchService.class);
        assertThat(pgVectorBootstrap.hnswIndexPresent()).isTrue();

        seedCorpus();
        int chunks = DOCUMENTS * CHUNKS_PER_DOCUMENT;
        assertThat(jdbcTemplate.queryForObject("select count(*) from document_chunk", Integer.class))
                .isEqualTo(chunks);

        String question = "How do I handle incident escalation for critical outages?";
        float[] queryVector = embeddingService.embed(question);

        double inMemoryMs = medianMillis(
                () -> inMemorySearch.search(question, queryVector, TOP_K, 0.05, null));
        double pgVectorMs = medianMillis(
                () -> chunkSearch.search(question, queryVector, TOP_K, 0.05, null));

        System.out.printf(Locale.ROOT, """
                Retrieval benchmark over %d chunks (top-k %d, median of %d runs)
                  in-memory scan : %8.2f ms
                  pgvector HNSW  : %8.2f ms
                """, chunks, TOP_K, ITERATIONS, inMemoryMs, pgVectorMs);

        assertThat(inMemorySearch.search(question, queryVector, TOP_K, 0.05, null))
                .hasSizeLessThanOrEqualTo(TOP_K)
                .isNotEmpty();
        assertThat(chunkSearch.search(question, queryVector, TOP_K, 0.05, null))
                .hasSizeLessThanOrEqualTo(TOP_K)
                .isNotEmpty();
    }

    /**
     * Writes documents and chunks straight through JDBC: going through the
     * ingestion service would time embedding and duplicate checks rather than
     * retrieval, and the trigger needs to be exercised anyway.
     */
    private void seedCorpus() {
        String insertDocument = """
                insert into knowledge_document (title, content, size_bytes, chunk_count, created_at)
                values (?, ?, ?, ?, now()) returning id
                """;
        String insertChunk = """
                insert into document_chunk (document_id, chunk_index, content, embedding, token_count)
                values (?, ?, ?, ?, ?)
                """;

        for (int document = 0; document < DOCUMENTS; document++) {
            List<String> chunks = new ArrayList<>(CHUNKS_PER_DOCUMENT);
            StringBuilder documentText = new StringBuilder();
            for (int index = 0; index < CHUNKS_PER_DOCUMENT; index++) {
                String chunk = "During an incident, escalation to the on-call engineer is critical for "
                        + "outages. Runbook " + document + "-" + index + " covers service_"
                        + ((document * CHUNKS_PER_DOCUMENT + index) % 997) + ".";
                chunks.add(chunk);
                documentText.append(chunk).append(' ');
            }

            Long documentId = jdbcTemplate.queryForObject(insertDocument, Long.class,
                    "Benchmark runbook " + document, documentText.toString(),
                    documentText.length(), CHUNKS_PER_DOCUMENT);
            List<Object[]> rows = new ArrayList<>(CHUNKS_PER_DOCUMENT);
            for (int index = 0; index < chunks.size(); index++) {
                String content = chunks.get(index);
                rows.add(new Object[]{
                        documentId,
                        index,
                        content,
                        EmbeddingCodec.encode(embeddingService.embed(content)),
                        content.split("\\s+").length
                });
            }
            jdbcTemplate.batchUpdate(insertChunk, rows);
        }
    }

    private double medianMillis(Runnable query) {
        for (int i = 0; i < WARMUP; i++) {
            query.run();
        }
        long[] samples = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            long startedAt = System.nanoTime();
            query.run();
            samples[i] = System.nanoTime() - startedAt;
        }
        Arrays.sort(samples);
        return samples[ITERATIONS / 2] / 1_000_000.0;
    }
}
