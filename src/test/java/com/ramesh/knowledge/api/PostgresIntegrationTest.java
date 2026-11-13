package com.ramesh.knowledge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramesh.knowledge.search.ChunkSearch;
import com.ramesh.knowledge.search.PgVectorBootstrap;
import com.ramesh.knowledge.search.PgVectorSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the application against a real Postgres instance, because H2 accepts
 * DDL and SQL that Postgres rejects. The container is reused for every test in
 * the class and the datasource is wired by {@link ServiceConnection}, so the
 * profile only supplies the Postgres-specific Hibernate settings.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private static final String DOCUMENT_TEXT = """
            The support runbook explains how to escalate a production incident.
            A severity one incident pages the on-call engineer immediately.
            Every incident needs a written summary within two working days.
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChunkSearch chunkSearch;

    @Autowired
    private PgVectorBootstrap pgVectorBootstrap;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ingestsAsksReindexesAndDeletesAgainstPostgres() throws Exception {
        assertThat(chunkSearch).isInstanceOf(PgVectorSearchService.class);
        assertThat(pgVectorBootstrap.hnswIndexPresent()).isTrue();
        MvcResult created = mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Support Runbook",
                                "content", DOCUMENT_TEXT))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chunkCount").value(greaterThanOrEqualTo(1)))
                .andReturn();

        long documentId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        Integer mirrored = jdbcTemplate.queryForObject(
                "select count(*) from document_chunk where document_id = ? and embedding_vector is not null",
                Integer.class, documentId);
        assertThat(mirrored).isEqualTo(1);

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "When does a severity one incident page the on-call engineer?",
                                "documentId", documentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].documentId").value(documentId))
                .andExpect(jsonPath("$.sources[0].documentTitle").value("Support Runbook"));

        mockMvc.perform(get("/api/documents/page")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/api/documents/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(1));

        mockMvc.perform(get("/api/documents/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(1));

        mockMvc.perform(delete("/api/documents/{id}", documentId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isNotFound());
    }
}
