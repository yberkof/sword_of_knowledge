package com.sok.backend.persistence;

import java.util.List;

/** Row from {@code sword_of_knowledge.questions} for MCQ duels. */
public record QuestionRecord(
    String id, String text, List<String> options, int correctIndex, String category) {}
