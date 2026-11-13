package com.ramesh.knowledge.chat.dto;

import java.time.Instant;

public record FeedbackView(
        Long id,
        String answerId,
        boolean helpful,
        String comment,
        Instant createdAt
) {
}
