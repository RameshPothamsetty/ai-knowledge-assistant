package com.ramesh.knowledge.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramesh.knowledge.support.DocumentFixtures;
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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
    void exposesChunksForPreview() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Chunk Preview Notes",
                                "content", "First sentence about tomatoes. ".repeat(80)))))
                .andExpect(status().isCreated())
                .andReturn();

        long documentId = readId(created);

        mockMvc.perform(get("/api/documents/{id}/chunks", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chunkIndex").value(0))
                .andExpect(jsonPath("$[0].content").value(not(emptyString())))
                .andExpect(jsonPath("$[0].tokenCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[1].chunkIndex").value(1))
                .andExpect(jsonPath("$[0].embedding").doesNotExist());

        mockMvc.perform(get("/api/documents/{id}/chunks", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void restrictsAnswersToTheRequestedDocument() throws Exception {
        long javaId = createDocument("Java Notes",
                "Java streams use filter and map to process collections of values.");
        long breadId = createDocument("Bread Notes",
                "Sourdough bread needs yeast, water and a warm kitchen to rise.");

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.<String, Object>of(
                                "question", "How do filters and maps work on streams and collections?",
                                "documentId", javaId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].documentId").value(javaId))
                .andExpect(jsonPath("$.sources[*].documentTitle", not(hasItem("Bread Notes"))));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.<String, Object>of(
                                "question", "How do filters and maps work on streams and collections?",
                                "documentId", breadId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[*].documentTitle", not(hasItem("Java Notes"))));
    }

    @Test
    void pagesDocumentsNewestFirst() throws Exception {
        createDocument("Page One", "Pagination splits a long document list into pages.");
        createDocument("Page Two", "The second page continues the document listing.");
        createDocument("Page Three", "A third document makes two pages of two items.");

        mockMvc.perform(get("/api/documents/page").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.totalPages").value(greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/documents/page").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("size")));
    }

    @Test
    void reindexesTheKnowledgeBaseWithoutLosingDocuments() throws Exception {
        long documentId = createDocument("Reindex Notes",
                "Vector search compares embeddings of the question and every chunk.");

        mockMvc.perform(post("/api/documents/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.chunks").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.durationMs").value(greaterThanOrEqualTo(0)));

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/documents/{id}/chunks", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value(containsString("embeddings")));
    }

    @Test
    void acceptsAnswerStyleAndConversationId() throws Exception {
        createDocument("Style Notes", "Spring Boot starts an embedded Tomcat server by default.");

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "What server does Spring Boot start?",
                                "length", "short",
                                "tone", "technical",
                                "conversationId", "it-conversation"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(not(emptyString())));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "Can I change the port?",
                                "conversationId", "it-conversation"))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnUnknownAnswerLengthWithTheStandardErrorBody() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "question",
                                "length", "epic"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("malformed request body"));
    }

    @Test
    void streamsAnswersAsServerSentEvents() throws Exception {
        createDocument("Streaming Notes", "The Actuator module exposes health and metrics endpoints.");

        MvcResult result = mockMvc.perform(post("/api/ask/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "Which module exposes health checks?"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(10_000);

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:sources")))
                .andExpect(content().string(containsString("event:token")))
                .andExpect(content().string(containsString("event:done")));
    }

    @Test
    void recordsFeedbackForAnAnswer() throws Exception {
        MvcResult answered = mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "How does vector search compare embeddings?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerId").value(not(emptyString())))
                .andReturn();

        String answerId = objectMapper.readTree(answered.getResponse().getContentAsString())
                .get("answerId").asText();

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "answerId", answerId,
                                "helpful", true,
                                "comment", "Clear answer"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answerId").value(answerId))
                .andExpect(jsonPath("$.helpful").value(true))
                .andExpect(jsonPath("$.comment").value("Clear answer"));

        mockMvc.perform(get("/api/feedback/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.helpful").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "answerId", "",
                                "helpful", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(containsString("answerId"))));
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
    void rejectsDuplicateDocumentsWithConflict() throws Exception {
        String content = "Tomato seedlings need six hours of direct sunlight and consistent watering.";

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Gardening Notes",
                                "content", content))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Gardening Notes",
                                "content", content))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Gardening Notes, revised",
                                "content", content))))
                .andExpect(status().isCreated());
    }

    @Test
    void uploadsTextPdfAndDocxFilesAndRejectsUnsupportedFormats() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "notes.txt", "text/plain",
                "Docker compose runs the application and a Postgres database.".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents/upload").file(textFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("notes"))
                .andExpect(jsonPath("$.sourceName").value("notes.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.sizeBytes").value(greaterThan(0)));

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "architecture.pdf", "application/pdf",
                DocumentFixtures.pdf("The vector store keeps one embedding per chunk."));

        mockMvc.perform(multipart("/api/documents/upload").file(pdfFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("architecture"))
                .andExpect(jsonPath("$.sourceName").value("architecture.pdf"))
                .andExpect(jsonPath("$.chunkCount").value(1));

        MockMultipartFile docxFile = new MockMultipartFile(
                "file", "policy.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                DocumentFixtures.docx("Refunds are accepted within thirty days of purchase."));

        mockMvc.perform(multipart("/api/documents/upload").file(docxFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceName").value("policy.docx"));

        MockMultipartFile rtfFile = new MockMultipartFile(
                "file", "legacy.rtf", "application/rtf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/documents/upload").file(rtfFile))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));

        MockMultipartFile corruptPdf = new MockMultipartFile(
                "file", "scan.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/documents/upload").file(corruptPdf))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void rejectsOversizedDocumentsWithPayloadTooLarge() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Huge Notes",
                                "content", "a".repeat(200_001)))))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value(containsString("limit is 200000")));
    }

    @Test
    void summarisesTheKnowledgeBase() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Summary Source",
                                "content", "Metrics help operators see how retrieval behaves under load."))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/documents/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.chunks").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.tokens").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void exposesTheRetrievalConfigurationUsedByTheUi() throws Exception {
        mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingModel").value(not(emptyString())))
                .andExpect(jsonPath("$.embeddingDimensions").value(256))
                .andExpect(jsonPath("$.defaultTopK").value(4))
                .andExpect(jsonPath("$.maxTopK").value(20));
    }

    @Test
    void publishesAnOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AI Knowledge Assistant API"))
                .andExpect(jsonPath("$.paths['/api/ask']").exists())
                .andExpect(jsonPath("$.paths['/api/documents']").exists())
                .andExpect(jsonPath("$.paths['/api/feedback']").exists());
    }

    @Test
    void echoesCorrelationIdsOnResponsesAndErrors() throws Exception {
        mockMvc.perform(get("/api/documents/{id}", 999_999).header("X-Request-Id", "trace-abc"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "trace-abc"))
                .andExpect(jsonPath("$.requestId").value("trace-abc"));

        mockMvc.perform(get("/api/documents/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", not(emptyString())));
    }

    @Test
    void exposesRetrievalAndGenerationTimers() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "How does retrieval rank chunks?"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/rag.retrieval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("rag.retrieval"))
                .andExpect(jsonPath("$.measurements[0].value").value(greaterThanOrEqualTo(0.0)));

        mockMvc.perform(get("/actuator/metrics/rag.answers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("rag.answers"));
    }

    @Test
    void returnsNotFoundForUnknownDocuments() throws Exception {
        mockMvc.perform(get("/api/documents/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private long createDocument(String title, String content) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "content", content))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(created);
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }
}
