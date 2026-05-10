package com.sok.backend.persistence;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserQuestionHistoryRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public UserQuestionHistoryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void recordQuestion(String userId, String questionId, String questionType) {
    jdbcTemplate.update(
        "INSERT INTO " + SCH + ".user_question_history (user_id, question_id, question_type) " +
        "VALUES (?, ?, ?) ON CONFLICT (user_id, question_id, question_type) DO NOTHING",
        userId, questionId, questionType);
  }

  public List<String> getRecentQuestionIds(String userId, String questionType, int limit) {
    return jdbcTemplate.queryForList(
        "SELECT question_id FROM " + SCH + ".user_question_history " +
        "WHERE user_id = ? AND question_type = ? " +
        "ORDER BY answered_at DESC LIMIT ?",
        String.class,
        userId, questionType, limit);
  }
}
