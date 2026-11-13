package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.chat.dto.FeedbackSummary;
import com.ramesh.knowledge.chat.dto.FeedbackView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Stores helpful / not-helpful votes per answer. The answer id is accepted as
 * given: answers are not persisted, so there is nothing to look up, and a vote
 * that arrives after a restart is still useful feedback.
 */
@Service
public class FeedbackService {

    private final AnswerFeedbackRepository repository;

    public FeedbackService(AnswerFeedbackRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public FeedbackView submit(String answerId, boolean helpful, String comment) {
        if (answerId == null || answerId.isBlank()) {
            throw new IllegalArgumentException("answerId must not be blank");
        }
        AnswerFeedback saved = repository.save(new AnswerFeedback(
                answerId.trim(),
                helpful,
                comment == null || comment.isBlank() ? null : comment.trim(),
                Instant.now()));
        return new FeedbackView(saved.getId(), saved.getAnswerId(), saved.isHelpful(),
                saved.getComment(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public FeedbackSummary summary() {
        long helpful = repository.countHelpful();
        long notHelpful = repository.countNotHelpful();
        return new FeedbackSummary(helpful + notHelpful, helpful, notHelpful);
    }
}
