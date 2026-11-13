package com.ramesh.knowledge.search;

import com.ramesh.knowledge.text.Terms;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Okapi BM25 over the chunks of a single retrieval scope.
 *
 * <p>Cosine similarity on hashing embeddings already rewards shared words, but
 * it treats every dimension alike: a question about a rare identifier is
 * swamped by common words. BM25 adds an inverse document frequency weight, so
 * terms that appear in only one chunk dominate the ranking.
 *
 * <p>Scores are divided by the best score in the batch, which turns them into
 * {@code [0, 1]} values that can be blended with cosine similarity. The corpus
 * statistics are computed per search, exactly like the cosine scan, which keeps
 * the implementation stateless.
 */
public final class Bm25Scorer {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private Bm25Scorer() {
    }

    /**
     * @param query     the user question; {@code null} or a question with no
     *                  usable terms scores everything zero
     * @param documents chunk texts in the retrieval scope
     * @return one normalised score per document, in the same order
     */
    public static double[] score(String query, List<String> documents) {
        double[] scores = new double[documents.size()];
        if (query == null || documents.isEmpty()) {
            return scores;
        }
        List<String> queryTerms = Terms.of(query);
        if (queryTerms.isEmpty()) {
            return scores;
        }

        List<List<String>> documentTerms = documents.stream().map(Terms::of).toList();
        double averageLength = documentTerms.stream().mapToInt(List::size).average().orElse(0);
        if (averageLength == 0) {
            return scores;
        }

        Set<String> uniqueTerms = new LinkedHashSet<>(queryTerms);
        Map<String, Integer> documentFrequencies = new HashMap<>();
        for (String term : uniqueTerms) {
            int frequency = 0;
            for (List<String> terms : documentTerms) {
                if (terms.contains(term)) {
                    frequency++;
                }
            }
            documentFrequencies.put(term, frequency);
        }

        double best = 0;
        for (int i = 0; i < documentTerms.size(); i++) {
            List<String> terms = documentTerms.get(i);
            Map<String, Integer> termFrequencies = new HashMap<>();
            for (String term : terms) {
                termFrequencies.merge(term, 1, Integer::sum);
            }
            double score = 0;
            for (String term : uniqueTerms) {
                int frequency = termFrequencies.getOrDefault(term, 0);
                if (frequency == 0) {
                    continue;
                }
                int documentFrequency = documentFrequencies.get(term);
                double idf = Math.log(1 + (documents.size() - documentFrequency + 0.5) / (documentFrequency + 0.5));
                double lengthNorm = 1 - B + B * (terms.size() / averageLength);
                score += idf * (frequency * (K1 + 1)) / (frequency + K1 * lengthNorm);
            }
            scores[i] = score;
            best = Math.max(best, score);
        }

        if (best > 0) {
            for (int i = 0; i < scores.length; i++) {
                scores[i] = scores[i] / best;
            }
        }
        return scores;
    }
}
