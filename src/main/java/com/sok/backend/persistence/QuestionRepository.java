package com.sok.backend.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRepository {
  private static final String SCH = "sword_of_knowledge";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public QuestionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public int countActive() {
    Integer n =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*)::int FROM " + SCH + ".questions WHERE is_active = TRUE",
            Integer.class);
    return n == null ? 0 : n;
  }

  /**
   * Random active question in the given category (slug), e.g. {@code geo}, {@code general}.
   */
  public Optional<QuestionRecord> findRandomActiveByCategory(String category) {
    if (category == null || category.trim().isEmpty()) {
      return Optional.empty();
    }
    List<QuestionRecord> rows =
        jdbcTemplate.query(
            "SELECT id, text, options, correct_index, category FROM "
                + SCH
                + ".questions WHERE is_active = TRUE AND category = ? ORDER BY random() LIMIT 1",
            questionMapper(),
            category.trim());
    return rows.stream().findFirst();
  }

  /** Any random active question (fallback when category has no rows). */
  public Optional<QuestionRecord> findRandomActiveAny() {
    List<QuestionRecord> rows =
        jdbcTemplate.query(
            "SELECT id, text, options, correct_index, category FROM "
                + SCH
                + ".questions WHERE is_active = TRUE ORDER BY random() LIMIT 1",
            questionMapper());
    return rows.stream().findFirst();
  }

  private RowMapper<QuestionRecord> questionMapper() {
    return (ResultSet rs, int rowNum) -> mapRow(rs);
  }

  private QuestionRecord mapRow(ResultSet rs) throws SQLException {
    UUID id = rs.getObject("id", UUID.class);
    String text = rs.getString("text");
    String optionsJson = rs.getString("options");
    List<String> options = parseOptions(optionsJson);
    int correctIndex = rs.getInt("correct_index");
    String category = rs.getString("category");
    return new QuestionRecord(
        id != null ? id.toString() : "",
        text,
        options,
        correctIndex,
        category != null ? category : "");
  }

  private List<String> parseOptions(String json) {
    if (json == null || json.isEmpty()) {
      return Collections.emptyList();
    }
    try {
      List<String> list =
          objectMapper.readValue(json, new TypeReference<List<String>>() {});
      return list != null ? list : Collections.emptyList();
    } catch (Exception e) {
      throw new IllegalStateException("questions.options JSON invalid", e);
    }
  }
}
