package com.ramesh.knowledge.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:api-it;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class KnowledgeApiIntegrationTest {

    private static final String DOCUMENT_TEXT = """
            Spring Boot provides auto configuration for Spring applications.
            It starts an embedded Tomcat server by default.
            The Actuator module exposes health and metrics endpoints.
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ingestsAnswersListsAndDeletesADocument() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Spring Boot Guide",
                                "content", DOCUMENT_TEXT))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Spring Boot Guide"))
                .andExpect(jsonPath("$.chunkCount").value(greaterThanOrEqualTo(1)))
                .andReturn();

        long documentId = readId(created);

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "How does Spring Boot provide health checks?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(not(emptyString())))
                .andExpect(jsonPath("$.model").value("extractive-fallback-v1"))
                .andExpect(jsonPath("$.sources[0].documentId").value(documentId))
                .andExpect(jsonPath("$.sources[0].documentTitle").value("Spring Boot Guide"))
                .andExpect(jsonPath("$.sources[0].snippet").value(containsString("Actuator")));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Spring Boot Guide")));

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId));

        mockMvc.perform(delete("/api/documents/{id}", documentId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersWithAHonestMessageWhenTheKnowledgeBaseIsEmpty() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "What is the airspeed velocity of an unladen swallow?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.answer").value(containsString("could not find anything relevant")));
    }

    @Test
    void rejectsInvalidDocumentsWithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "",
                                "content", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("validation failed"))
                .andExpect(jsonPath("$.details", hasItem(containsString("title"))));
    }

    @Test
    void uploadsPlainTextFilesAndRejectsUnsupportedFormats() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain",
                "Docker compose runs the application and a Postgres database.".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents/upload").file(textFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("notes"))
                .andExpect(jsonPath("$.sourceName").value("notes.txt"));

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "paper.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/documents/upload").file(pdfFile))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void returnsNotFoundForUnknownDocuments() throws Exception {
        mockMvc.perform(get("/api/documents/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }
}
