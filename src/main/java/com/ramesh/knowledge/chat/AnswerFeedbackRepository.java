package com.ramesh.knowledge.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnswerFeedbackRepository extends JpaRepository<AnswerFeedback, Long> {

    @Query("select count(f) from AnswerFeedback f where f.helpful = true")
    long countHelpful();

    @Query("select count(f) from AnswerFeedback f where f.helpful = false")
    long countNotHelpful();
}
