package com.ramesh.knowledge.chat;

import com.ramesh.knowledge.search.ScoredChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;

/**
 * Default, offline answer generator.
 *
 * <p>It does not invent text: it selects the sentences from the retrieved
 * chunks that overlap most with the question and stitches them together with
 * citation markers. That makes the whole RAG pipeline demonstrable and testable
 * without an API key, and the interface allows a hosted LLM to replace it.
 */
@Service
public class ExtractiveLlmClient implements LlmClient {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+|\\R+");
    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    private static final int MAX_SENTENCES = 3;

    @Override
    public String complete(LlmRequest request) {
        if (request.sources().isEmpty()) {
            return "I could not find anything relevant in the uploaded documents.";
        }

        Set<String> questionTerms = terms(request.question());
        List<Sentence> candidates = new ArrayList<>();
        for (int sourceIndex = 0; sourceIndex < request.sources().size(); sourceIndex++) {
            ScoredChunk source = request.sources().get(sourceIndex);
            for (String sentence : SENTENCE_SPLIT.split(source.content())) {
                String cleaned = sentence.trim();
                if (cleaned.length() < 20) {
                    continue;
                }
                double overlap = overlap(questionTerms, terms(cleaned)) * source.score();
                candidates.add(new Sentence(cleaned, sourceIndex + 1, overlap));
            }
        }

        candidates.sort((left, right) -> Double.compare(right.score(), left.score()));

        // Sentences that share no word with the question only add noise, so they
        // are dropped even when their chunk was retrieved.
        List<Sentence> selected = candidates.stream()
                .filter(candidate -> candidate.score() > 0)
                .limit(MAX_SENTENCES)
                .toList();

        if (selected.isEmpty()) {
            return "I could not find an answer to that question in the uploaded documents.";
        }

        // Cite once per source instead of after every sentence.
        Map<Integer, List<String>> groupedBySource = new LinkedHashMap<>();
        for (Sentence sentence : selected) {
            groupedBySource.computeIfAbsent(sentence.sourceNumber(), key -> new ArrayList<>())
                    .add(sentence.text());
        }

        return groupedBySource.entrySet().stream()
                .map(entry -> entry.getValue().stream().collect(joining(" ")) + " [" + entry.getKey() + "]")
                .collect(joining(" "));
    }

    private Set<String> terms(String text) {
        Set<String> terms = new HashSet<>();
        var matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() > 2) {
                terms.add(word);
            }
        }
        return terms;
    }

    private double overlap(Set<String> questionTerms, Set<String> sentenceTerms) {
        if (questionTerms.isEmpty() || sentenceTerms.isEmpty()) {
            return 0.0;
        }
        long shared = questionTerms.stream().filter(sentenceTerms::contains).count();
        return (double) shared / questionTerms.size();
    }

    @Override
    public String modelName() {
        return "extractive-fallback-v1";
    }

    private record Sentence(String text, int sourceNumber, double score) {
    }
}
