package com.ramesh.knowledge.document;

import com.ramesh.knowledge.config.RagProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits documents into overlapping chunks that are small enough to embed and
 * retrieve. Splitting is sentence aware so a chunk never stops mid-sentence
 * unless a single sentence is longer than the configured chunk size, in which
 * case it is split on word boundaries.
 */
@Service
public class ChunkingService {

    private static final Pattern UNIT_SPLIT = Pattern.compile("(?<=[.!?])\\s+|\\R+");
    private static final Pattern INLINE_WHITESPACE = Pattern.compile("[ \\t\\x0B\\f]+");

    private final int chunkSize;
    private final int chunkOverlap;

    @Autowired
    public ChunkingService(RagProperties properties) {
        this(properties.chunkSize(), properties.chunkOverlap());
    }

    ChunkingService(int chunkSize, int chunkOverlap) {
        if (chunkSize < 50) {
            throw new IllegalArgumentException("chunkSize must be at least 50 characters");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap must be between 0 and chunkSize - 1");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> units = new ArrayList<>();
        for (String rawUnit : UNIT_SPLIT.split(normalize(text))) {
            String unit = rawUnit.trim();
            if (!unit.isEmpty()) {
                units.add(unit);
            }
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String unit : units) {
            List<String> pieces = unit.length() > chunkSize ? splitOnWordBoundaries(unit) : List.of(unit);
            for (String piece : pieces) {
                if (current.length() > 0 && current.length() + 1 + piece.length() > chunkSize) {
                    String finished = current.toString();
                    addChunk(chunks, finished);
                    current = new StringBuilder(overlapTail(finished));
                    if (current.length() + 1 + piece.length() > chunkSize) {
                        // The carried overlap plus this piece would overflow, so the
                        // overlap is dropped rather than emitted as a tiny chunk.
                        current.setLength(0);
                    }
                    if (current.length() > 0) {
                        current.append(' ');
                    }
                }
                if (current.length() > 0 && current.charAt(current.length() - 1) != ' ') {
                    current.append(' ');
                }
                current.append(piece);
            }
        }
        addChunk(chunks, current.toString());
        return List.copyOf(chunks);
    }

    /**
     * Collapses Windows line endings and runs of spaces so chunk boundaries are
     * deterministic regardless of how the text was pasted.
     */
    private String normalize(String text) {
        return INLINE_WHITESPACE
                .matcher(text.replace("\r\n", "\n").replace('\r', '\n'))
                .replaceAll(" ")
                .trim();
    }

    /**
     * Returns the trailing words of the previous chunk that should be repeated,
     * or an empty string when overlap is disabled or the chunk is too short.
     */
    private String overlapTail(String finishedChunk) {
        if (chunkOverlap == 0 || finishedChunk.length() <= chunkOverlap) {
            return "";
        }
        String tail = finishedChunk.substring(finishedChunk.length() - chunkOverlap);
        int firstSpace = tail.indexOf(' ');
        if (firstSpace >= 0 && firstSpace + 1 < tail.length()) {
            tail = tail.substring(firstSpace + 1);
        }
        return tail.trim();
    }

    /**
     * Breaks a single oversized sentence into pieces of at most {@code chunkSize}
     * characters, preferring to end each piece at a space.
     */
    private List<String> splitOnWordBoundaries(String unit) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < unit.length()) {
            int end = Math.min(unit.length(), start + chunkSize);
            if (end < unit.length()) {
                int lastSpace = unit.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            String piece = unit.substring(start, end).trim();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            start = end;
            while (start < unit.length() && unit.charAt(start) == ' ') {
                start++;
            }
        }
        return pieces;
    }

    private void addChunk(List<String> chunks, String candidate) {
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (chunks.isEmpty() || !chunks.get(chunks.size() - 1).equals(trimmed)) {
            chunks.add(trimmed);
        }
    }
}
