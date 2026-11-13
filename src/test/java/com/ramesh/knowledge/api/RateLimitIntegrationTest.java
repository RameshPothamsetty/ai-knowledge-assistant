package com.ramesh.knowledge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rate-it;DB_CLOSE_DELAY=-1",
        "app.rate-limit.requests-per-minute=2"
})
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rejectsQuestionsBeyondTheWindowButLeavesOtherEndpointsAlone() throws Exception {
        String question = objectMapper.writeValueAsString(Map.of("question", "What is indexed?"));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/ask")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(question))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(question))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("too many questions, try again in a minute"));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk());
    }
}
