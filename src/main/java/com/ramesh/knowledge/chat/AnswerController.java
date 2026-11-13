package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.chat.dto.AnswerResponse;
import com.ramesh.knowledge.chat.dto.AskRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * Question answering endpoint backed by retrieval over the uploaded documents.
 */
@RestController
@RequestMapping("/api")
public class AnswerController {

    private static final long STREAM_TIMEOUT_MS = 60_000L;

    private final RagService ragService;
    private final ExecutorService answerStreamExecutor;

    public AnswerController(RagService ragService, ExecutorService answerStreamExecutor) {
        this.ragService = ragService;
        this.answerStreamExecutor = answerStreamExecutor;
    }

    @PostMapping("/ask")
    public AnswerResponse ask(@Valid @RequestBody AskRequest request) {
        return ragService.ask(
                request.question(),
                request.topK(),
                request.documentId(),
                new AnswerStyle(request.length(), request.tone()),
                request.conversationId());
    }

    /**
     * Same question, delivered as Server-Sent Events: one {@code sources} event
     * with the passages, then a {@code token} event per word, then {@code done}
     * with the model and latency. Errors are sent as a final {@code error} event
     * so the client can show a message instead of a broken stream.
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStreaming(@Valid @RequestBody AskRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        answerStreamExecutor.execute(() -> {
            try {
                RagService.StreamedAnswer result = ragService.askStreaming(
                        request.question(),
                        request.topK(),
                        request.documentId(),
                        new AnswerStyle(request.length(), request.tone()),
                        request.conversationId(),
                        sources -> send(emitter, "sources", sources),
                        token -> send(emitter, "token", token));
                send(emitter, "done", new StreamDone(result.answerId(), result.model(), result.latencyMs()));
                emitter.complete();
            } catch (RuntimeException exception) {
                try {
                    send(emitter, "error", exception.getMessage() == null
                            ? "answer generation failed" : exception.getMessage());
                } catch (RuntimeException ignored) {
                    // The client already went away; nothing left to report to.
                }
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            throw new IllegalStateException("stream closed", exception);
        }
    }

    private record StreamDone(String answerId, String model, long latencyMs) {
    }
}
