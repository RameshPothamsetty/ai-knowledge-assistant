package com.ramesh.knowledge.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Bm25ScorerTest {

    @Test
    void givesTheRareTermMoreWeightThanACommonOne() {
        List<String> documents = List.of(
                "deployment notes for the platform team",
                "deployment policy deployment policy",
                "kubernetes schedules containers across nodes"
        );

        double[] scores = Bm25Scorer.score("kubernetes deployment", documents);

        assertThat(scores[2]).isGreaterThan(scores[1]);
        assertThat(scores[2]).isGreaterThan(scores[0]);
        assertThat(scores[2]).isEqualTo(1.0);
    }

    @Test
    void prefersTheShorterDocumentWhenBothContainTheTerm() {
        double[] scores = Bm25Scorer.score("backup",
                List.of("backup", "backup " + "and other words ".repeat(40)));

        assertThat(scores[0]).isEqualTo(1.0);
        assertThat(scores[1]).isBetween(0.0, 1.0);
        assertThat(scores[1]).isLessThan(scores[0]);
    }

    @Test
    void scoresEverythingZeroWhenTheQueryHasNoUsableTerms() {
        assertThat(Bm25Scorer.score("the and of", List.of("some text", "other text")))
                .containsExactly(0.0, 0.0);
        assertThat(Bm25Scorer.score(null, List.of("some text"))).containsExactly(0.0);
        assertThat(Bm25Scorer.score("text", List.of())).isEmpty();
    }

    @Test
    void scoresNonMatchingDocumentsZero() {
        double[] scores = Bm25Scorer.score("tomato",
                List.of("tomato seedlings need sunlight", "sourdough bread needs yeast"));

        assertThat(scores[0]).isEqualTo(1.0);
        assertThat(scores[1]).isZero();
    }
}
