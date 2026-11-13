package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.chat.dto.FeedbackSummary;
import com.ramesh.knowledge.chat.dto.FeedbackView;
import com.ramesh.knowledge.chat.dto.SubmitFeedbackRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Collects helpful / not-helpful votes for answers returned by `/api/ask`.
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackView submit(@Valid @RequestBody SubmitFeedbackRequest request) {
        return feedbackService.submit(request.answerId(), request.helpful(), request.comment());
    }

    @GetMapping("/summary")
    public FeedbackSummary summary() {
        return feedbackService.summary();
    }
}
