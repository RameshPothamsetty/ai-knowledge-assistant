package com.ramesh.knowledge.chat;

/**
 * Length and tone requested for one answer. Missing values fall back to the
 * defaults so callers can send only the parts they care about.
 */
public record AnswerStyle(AnswerLength length, AnswerTone tone) {

    public static final AnswerStyle DEFAULT = new AnswerStyle(AnswerLength.MEDIUM, AnswerTone.NEUTRAL);

    public AnswerStyle {
        length = length == null ? AnswerLength.MEDIUM : length;
        tone = tone == null ? AnswerTone.NEUTRAL : tone;
    }
}
