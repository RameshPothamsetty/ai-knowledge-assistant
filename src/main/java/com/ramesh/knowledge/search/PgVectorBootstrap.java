package com.ramesh.knowledge.search;

import com.ramesh.knowledge.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Prepares the pgvector column, trigger and HNSW index when the application
 * starts against Postgres.
 *
 * <p>The existing {@code embedding} column stays the source of truth and the
 * schema stays portable: the JSON text is mirrored into a {@code vector}
 * column by a trigger, so ingestion code is unaware of the second
 * representation. Hibernate's {@code ddl-auto=update} cannot express a vector
 * column or an HNSW index, which is why this runs afterwards instead of in the
 * entity mapping.
 *
 * <p>Requires the {@code vector} extension to be installable; on managed
 * Postgres the extension usually has to be enabled once by the provider.
 */
@Component
@Profile("postgres")
public class PgVectorBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PgVectorBootstrap.class);

    private static final String TRIGGER_FUNCTION = """
            create or replace function sync_chunk_embedding_vector() returns trigger as $$
            begin
                if new.embedding is null then
                    new.embedding_vector := null;
                else
                    new.embedding_vector := cast(new.embedding as vector);
                end if;
                return new;
            end;
            $$ language plpgsql
            """;

    private final JdbcTemplate jdbcTemplate;
    private final int dimensions;

    public PgVectorBootstrap(JdbcTemplate jdbcTemplate, RagProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.dimensions = properties.embeddingDimensions();
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("create extension if not exists vector");
        jdbcTemplate.execute("alter table document_chunk add column if not exists embedding_vector vector("
                + dimensions + ")");
        jdbcTemplate.execute(TRIGGER_FUNCTION);
        jdbcTemplate.execute("drop trigger if exists trg_sync_chunk_embedding_vector on document_chunk");
        jdbcTemplate.execute("""
                create trigger trg_sync_chunk_embedding_vector
                before insert or update of embedding on document_chunk
                for each row execute function sync_chunk_embedding_vector()
                """);

        int backfilled = jdbcTemplate.update("""
                update document_chunk
                set embedding_vector = cast(embedding as vector)
                where embedding_vector is null
                """);
        if (backfilled > 0) {
            log.info("Backfilled {} chunk embeddings into pgvector", backfilled);
        }

        // Cosine distance is the operator the search orders by, so the index
        // must use the matching opclass.
        jdbcTemplate.execute("""
                create index if not exists idx_chunk_embedding_hnsw
                on document_chunk using hnsw (embedding_vector vector_cosine_ops)
                """);
    }

    /**
     * True when the HNSW index exists, used by tests and diagnostics rather
     * than by the search path.
     */
    public boolean hnswIndexPresent() {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from pg_indexes
                where tablename = 'document_chunk' and indexname = 'idx_chunk_embedding_hnsw'
                """, Integer.class);
        return count != null && count > 0;
    }
}
