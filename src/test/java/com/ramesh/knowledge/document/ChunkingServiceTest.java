package com.ramesh.knowledge.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService(80, 20);

    @Test
    void returnsNoChunksForBlankInput() {
        assertThat(chunkingService.chunk(null)).isEmpty();
        assertThat(chunkingService.chunk("   \n\t ")).isEmpty();
    }

    @Test
    void keepsShortTextInOneChunk() {
        List<String> chunks = chunkingService.chunk("Spring Boot starts an embedded Tomcat server.");

        assertThat(chunks).containsExactly("Spring Boot starts an embedded Tomcat server.");
    }

    @Test
    void normalisesWindowsLineEndingsAndRepeatedSpaces() {
        List<String> chunks = chunkingService.chunk("First line.\r\nSecond    line with   spaces.");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("First line. Second line with spaces.");
    }

    @Test
    void splitsLongTextIntoChunksWithinTheSizeLimit() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            text.append("Sentence number ").append(i).append(" explains topic ").append(i).append(". ");
        }

        List<String> chunks = chunkingService.chunk(text.toString());

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk).isNotBlank();
            assertThat(chunk.length()).isLessThanOrEqualTo(80);
        });
        assertThat(String.join(" ", chunks)).contains("Sentence number 0", "Sentence number 24");
    }

    @Test
    void consecutiveChunksShareOverlappingWords() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            text.append("Alpha").append(i).append(" beta").append(i).append(" gamma").append(i).append(". ");
        }

        List<String> chunks = chunkingService.chunk(text.toString());

        assertThat(chunks).hasSizeGreaterThan(1);
        String firstWordsOfSecondChunk = chunks.get(1).split("\\s+")[0];
        String tailOfFirstChunk = chunks.get(0).substring(Math.max(0, chunks.get(0).length() - 40));
        assertThat(tailOfFirstChunk).contains(firstWordsOfSecondChunk);
    }

    @Test
    void splitsOversizedSentenceOnWordBoundaries() {
        // Distinct words, so the split is what is under test rather than dedup.
        String sentence = java.util.stream.IntStream.range(0, 60)
                .mapToObj(index -> "token" + index)
                .collect(java.util.stream.Collectors.joining(" "));

        List<String> chunks = chunkingService.chunk(sentence);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk).isNotBlank();
            assertThat(chunk.length()).isLessThanOrEqualTo(80);
            assertThat(chunk.split("\\s+")).allMatch(word -> word.startsWith("token"));
        });
        assertThat(String.join(" ", chunks)).contains("token0").contains("token59");
    }

    @Test
    void dropsRepeatedBoilerplateChunks() {
        ChunkingService noOverlap = new ChunkingService(100, 0);
        String boilerplate = "Confidential internal document. Do not distribute.";
        String unique = "Tomato seedlings need six hours of direct sunlight and steady watering to grow well.";

        List<String> chunks = noOverlap.chunk(boilerplate + " " + unique + " " + boilerplate);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.stream().filter(chunk -> chunk.contains("Confidential")).count()).isEqualTo(1);
        assertThat(chunks).anyMatch(chunk -> chunk.contains("Tomato seedlings"));
    }

    @Test
    void treatsPunctuationAndCaseDifferencesAsTheSameBoilerplate() {
        ChunkingService noOverlap = new ChunkingService(100, 0);
        String first = "Confidential internal document. Do not distribute.";
        String second = "CONFIDENTIAL internal document, do not distribute!";
        String unique = "Tomato seedlings need six hours of direct sunlight and steady watering to grow well.";

        List<String> chunks = noOverlap.chunk(first + " " + unique + " " + second);

        assertThat(chunks.stream().filter(chunk -> chunk.toLowerCase().contains("confidential")).count())
                .isEqualTo(1);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new ChunkingService(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChunkingService(10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
