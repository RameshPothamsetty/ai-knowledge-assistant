package com.ramesh.knowledge.chat.dto;

/**
 * Vote totals across all answers. Kept tiny so it can sit in a status page.
 */
public record FeedbackSummary(
        long total,
        long helpful,
        long notHelpful
) {
}
