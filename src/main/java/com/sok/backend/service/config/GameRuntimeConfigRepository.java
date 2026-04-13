package com.sok.backend.service.config;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GameRuntimeConfigRepository {
  private final JdbcTemplate jdbcTemplate;

  public GameRuntimeConfigRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<String> findPayload() {
    List<String> rows =
        jdbcTemplate.query(
            "SELECT payload::text FROM sword_of_knowledge.game_runtime_config WHERE id = 1",
            (rs, n) -> rs.getString(1));
    if (rows.isEmpty()) return Optional.empty();
    return Optional.ofNullable(rows.get(0));
  }

  public void savePayload(String payloadJson) {
    jdbcTemplate.update(
        "INSERT INTO sword_of_knowledge.game_runtime_config(id, payload, updated_at) "
            + "VALUES (1, ?::jsonb, NOW()) "
            + "ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, updated_at = NOW()",
        payloadJson);
  }
}
