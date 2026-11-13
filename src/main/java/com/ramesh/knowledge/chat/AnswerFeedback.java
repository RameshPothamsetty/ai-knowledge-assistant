package com.ramesh.knowledge.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A user's verdict on one answer. Answers themselves are not stored — they are
 * cheap to regenerate — so the feedback keeps the answer id it refers to plus
 * the comment, which is what a reviewer actually needs.
 */
@Entity
@Table(name = "answer_feedback")
public class AnswerFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "answer_id", nullable = false, length = 64)
    private String answerId;

    @Column(nullable = false)
    private boolean helpful;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AnswerFeedback() {
        // Required by JPA.
    }

    public AnswerFeedback(String answerId, boolean helpful, String comment, Instant createdAt) {
        this.answerId = answerId;
        this.helpful = helpful;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getAnswerId() {
        return answerId;
    }

    public boolean isHelpful() {
        return helpful;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
