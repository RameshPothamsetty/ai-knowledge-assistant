package com.ramesh.knowledge.chat;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the last few question/answer pairs per conversation so a follow-up like
 * "and how do I disable it?" can be answered in context.
 *
 * <p>The store is in memory and therefore per instance: restarting the process
 * forgets every conversation, and a second replica would not see the first
 * one's turns. That is acceptable for a single-node assistant and keeps the
 * feature free of a schema change; persisting turns is a deployment concern.
 */
@Component
public class ConversationStore {

    /** Conversations kept before the store resets itself. */
    static final int MAX_CONVERSATIONS = 500;

    /** Turns remembered per conversation. */
    static final int MAX_TURNS = 6;

    private final Map<String, Deque<Turn>> conversations = new ConcurrentHashMap<>();

    public List<Turn> history(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        Deque<Turn> turns = conversations.get(conversationId);
        if (turns == null) {
            return List.of();
        }
        synchronized (turns) {
            return List.copyOf(turns);
        }
    }

    public void record(String conversationId, String question, String answer) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        if (conversations.size() >= MAX_CONVERSATIONS && !conversations.containsKey(conversationId)) {
            conversations.clear();
        }
        Deque<Turn> turns = conversations.computeIfAbsent(conversationId, key -> new ArrayDeque<>());
        synchronized (turns) {
            if (turns.size() == MAX_TURNS) {
                turns.removeFirst();
            }
            turns.addLast(new Turn(question, answer));
        }
    }

    int size() {
        return conversations.size();
    }

    public record Turn(String question, String answer) {

        /**
         * The most recent turns, newest last, without mutating the stored order.
         */
        public static List<Turn> recent(List<Turn> turns, int limit) {
            if (turns == null || turns.isEmpty() || limit <= 0) {
                return List.of();
            }
            int from = Math.max(0, turns.size() - limit);
            return new ArrayList<>(turns.subList(from, turns.size()));
        }
    }
}
