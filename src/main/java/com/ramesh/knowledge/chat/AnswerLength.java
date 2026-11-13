package com.ramesh.knowledge.chat;

/**
 * How much the answer may say. The sentence budget is enforced by the offline
 * extractive model and stated in the prompt for hosted models, which otherwise
 * only have the wording as guidance.
 */
public enum AnswerLength {

    SHORT(2),
    MEDIUM(3),
    LONG(5);

    private final int maxSentences;

    AnswerLength(int maxSentences) {
        this.maxSentences = maxSentences;
    }

    public int maxSentences() {
        return maxSentences;
    }
}
