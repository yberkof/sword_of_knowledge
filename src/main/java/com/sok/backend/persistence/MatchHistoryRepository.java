package com.sok.backend.persistence;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MatchHistoryRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbcTemplate;

  public MatchHistoryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public void saveMatchResult(
      String matchId, String mapId, String mode, List<ParticipantResult> participants) {
    jdbcTemplate.update(
        "INSERT INTO " + SCH + ".matches (id, map_id, mode, status, end_time) VALUES (?, ?, ?, 'ENDED', NOW()) " +
        "ON CONFLICT (id) DO UPDATE SET end_time = NOW(), status = 'ENDED'",
        matchId, mapId, mode);

    for (ParticipantResult p : participants) {
      jdbcTemplate.update(
          "INSERT INTO " + SCH + ".match_participants (match_id, user_id, place, score, xp_earned, trophies_delta) " +
          "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (match_id, user_id) DO UPDATE SET " +
          "place = EXCLUDED.place, score = EXCLUDED.score, xp_earned = EXCLUDED.xp_earned, trophies_delta = EXCLUDED.trophies_delta",
          matchId, p.userId, p.place, p.score, p.xpEarned, p.trophiesDelta);
    }
  }

  public List<Map<String, Object>> getMatchHistory(String userId, int limit, int offset) {
    return jdbcTemplate.queryForList(
        "SELECT m.id, m.map_id, m.mode, m.end_time, mp.place, mp.score, mp.xp_earned, mp.trophies_delta " +
        "FROM " + SCH + ".matches m " +
        "JOIN " + SCH + ".match_participants mp ON m.id = mp.match_id " +
        "WHERE mp.user_id = ? " +
        "ORDER BY m.end_time DESC LIMIT ? OFFSET ?",
        userId, limit, offset);
  }

  public record ParticipantResult(String userId, int place, int score, int xpEarned, int trophiesDelta) {}
}
