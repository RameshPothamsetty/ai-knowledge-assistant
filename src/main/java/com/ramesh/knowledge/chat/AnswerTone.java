package com.ramesh.knowledge.chat;

/**
 * Voice of the answer. The system prompt keeps its grounding rules regardless of
 * tone; only the delivery changes.
 */
public enum AnswerTone {

    NEUTRAL("Answer plainly and directly."),
    FRIENDLY("Answer in a warm, conversational tone."),
    TECHNICAL("Answer precisely, keeping technical terms and no filler.");

    private final String instruction;

    AnswerTone(String instruction) {
        this.instruction = instruction;
    }

    public String instruction() {
        return instruction;
    }
}
