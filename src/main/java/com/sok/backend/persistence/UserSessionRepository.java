package com.sok.backend.persistence;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserSessionRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public UserSessionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<String> getSummary(String userId, LocalDate date) {
    return jdbcTemplate.query(
        "SELECT summary_text FROM " + SCH + ".user_sessions WHERE user_id = ? AND session_date = ?",
        (rs, rowNum) -> rs.getString("summary_text"),
        userId, date).stream().findFirst();
  }

  public void saveSummary(String userId, LocalDate date, String summary) {
    jdbcTemplate.update(
        "INSERT INTO " + SCH + ".user_sessions (user_id, session_date, summary_text) VALUES (?, ?, ?) " +
        "ON CONFLICT (user_id, session_date) DO UPDATE SET summary_text = EXCLUDED.summary_text",
        userId, date, summary);
  }
}
