package com.sok.backend.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class NumericQuestionRepository {
  private static final String SCH = "sword_of_knowledge";

  private final JdbcTemplate jdbcTemplate;

  public NumericQuestionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public int countActive() {
    Integer n =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*)::int FROM " + SCH + ".numeric_questions WHERE is_active = TRUE",
            Integer.class);
    return n == null ? 0 : n;
  }

  public Optional<NumericQuestionRecord> findRandomActiveByCategory(String category) {
    if (category == null || category.trim().isEmpty()) {
      return Optional.empty();
    }
    List<NumericQuestionRecord> rows =
        jdbcTemplate.query(
            "SELECT id, text, correct_answer, category FROM "
                + SCH
                + ".numeric_questions WHERE is_active = TRUE AND category = ? ORDER BY random() LIMIT 1",
            numericMapper(),
            category.trim());
    return rows.stream().findFirst();
  }

  public Optional<NumericQuestionRecord> findRandomActiveAny() {
    List<NumericQuestionRecord> rows =
        jdbcTemplate.query(
            "SELECT id, text, correct_answer, category FROM "
                + SCH
                + ".numeric_questions WHERE is_active = TRUE ORDER BY random() LIMIT 1",
            numericMapper());
    return rows.stream().findFirst();
  }

  private RowMapper<NumericQuestionRecord> numericMapper() {
    return (ResultSet rs, int rowNum) -> mapRow(rs);
  }

  private static NumericQuestionRecord mapRow(ResultSet rs) throws SQLException {
    UUID id = rs.getObject("id", UUID.class);
    String text = rs.getString("text");
    int answer = rs.getInt("correct_answer");
    String category = rs.getString("category");
    return new NumericQuestionRecord(
        id != null ? id.toString() : "",
        text,
        answer,
        category != null ? category : "");
  }
}
