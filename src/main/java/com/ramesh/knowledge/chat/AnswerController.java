package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.chat.dto.AnswerResponse;
import com.ramesh.knowledge.chat.dto.AskRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Question answering endpoint backed by retrieval over the uploaded documents.
 */
@RestController
@RequestMapping("/api")
public class AnswerController {

    private final RagService ragService;

    public AnswerController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public AnswerResponse ask(@Valid @RequestBody AskRequest request) {
        return ragService.ask(request.question(), request.topK());
    }
}
