package com.sok.backend.persistence;

/** Row from {@code sword_of_knowledge.numeric_questions} (estimation rounds). */
public record NumericQuestionRecord(String id, String text, int correctAnswer, String category) {}
