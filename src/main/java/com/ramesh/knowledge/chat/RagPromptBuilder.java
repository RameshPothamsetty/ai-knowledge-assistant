package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.search.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Renders the retrieval augmented prompt. Keeping prompt text in one place
 * makes it easy to tune grounding rules and to test the exact wording.
 */
@Component
public class RagPromptBuilder {

    static final int MAX_CONTEXT_CHARS = 6000;

    private static final String SYSTEM_PROMPT = """
            You are the AI Knowledge Assistant, a question answering system that answers \
            strictly from the documents provided by the user.

            Rules:
            1. Use only the numbered context passages. Do not rely on outside knowledge.
            2. Cite the passage you used after each statement, for example [2].
            3. If the context does not contain the answer, say that the documents do not \
            cover the question instead of guessing.
            4. Answer in at most four sentences and quote short phrases where helpful.
            """;

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String userPrompt(String question, List<ScoredChunk> sources) {
        StringBuilder prompt = new StringBuilder(512);
        prompt.append("Context passages:\n\n");
        int used = 0;
        for (int i = 0; i < sources.size(); i++) {
            ScoredChunk source = sources.get(i);
            String passage = source.content().trim();
            if (used + passage.length() > MAX_CONTEXT_CHARS) {
                passage = passage.substring(0, Math.max(0, MAX_CONTEXT_CHARS - used)).trim();
            }
            if (passage.isEmpty()) {
                break;
            }
            prompt.append('[').append(i + 1).append("] ")
                    .append(source.documentTitle())
                    .append(" (chunk ").append(source.chunkIndex() + 1).append(")\n")
                    .append(passage).append("\n\n");
            used += passage.length();
            if (used >= MAX_CONTEXT_CHARS) {
                break;
            }
        }
        prompt.append("Question: ").append(question.trim()).append('\n');
        prompt.append("Answer using only the passages above, with citation markers.");
        return prompt.toString();
    }
}
