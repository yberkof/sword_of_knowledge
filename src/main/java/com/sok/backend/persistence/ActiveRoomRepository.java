package com.sok.backend.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActiveRoomRepository {
  private static final String SCH = "sword_of_knowledge";
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public ActiveRoomRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public void upsert(
      String roomId,
      String phase,
      String playerUidsJson,
      String inviteCode,
      String snapshotJson,
      String hostUid,
      String matchMode,
      String rulesetId,
      String mapId,
      Timestamp matchStartedAt) {
    jdbc.update(
        "INSERT INTO "
            + SCH
            + ".active_rooms ("
            + " room_id, phase, player_uids, invite_code, snapshot_json, host_uid, match_mode, ruleset_id, map_id, match_started_at, updated_at, version) "
            + " VALUES (?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, NOW(), 1) "
            + " ON CONFLICT (room_id) DO UPDATE SET "
            + " phase = EXCLUDED.phase, "
            + " player_uids = EXCLUDED.player_uids, "
            + " invite_code = EXCLUDED.invite_code, "
            + " snapshot_json = EXCLUDED.snapshot_json, "
            + " host_uid = EXCLUDED.host_uid, "
            + " match_mode = EXCLUDED.match_mode, "
            + " ruleset_id = EXCLUDED.ruleset_id, "
            + " map_id = EXCLUDED.map_id, "
            + " match_started_at = EXCLUDED.match_started_at, "
            + " updated_at = NOW(), "
            + " version = "
            + SCH
            + ".active_rooms.version + 1",
        roomId,
        phase,
        playerUidsJson,
        inviteCode,
        snapshotJson,
        hostUid,
        matchMode,
        rulesetId,
        mapId,
        matchStartedAt);
  }

  public void deleteById(String roomId) {
    jdbc.update("DELETE FROM " + SCH + ".active_rooms WHERE room_id = ?", roomId);
  }

  public List<ActiveRoomRow> findAll() {
    return jdbc.query(
        "SELECT room_id, phase, player_uids, invite_code, snapshot_json::text AS snapshot_json_text, host_uid, match_mode, ruleset_id, map_id, match_started_at, updated_at, version "
            + " FROM "
            + SCH
            + ".active_rooms",
        this::mapRow);
  }

  public Optional<ActiveRoomRow> findByRoomId(String roomId) {
    List<ActiveRoomRow> rows =
        jdbc.query(
            "SELECT room_id, phase, player_uids, invite_code, snapshot_json::text AS snapshot_json_text, host_uid, match_mode, ruleset_id, map_id, match_started_at, updated_at, version "
                + " FROM "
                + SCH
                + ".active_rooms WHERE room_id = ?",
            this::mapRow,
            roomId);
    return rows.stream().findFirst();
  }

  /** Safe uid containment on JSON array (no SQL string concat of uid into JSON literal). */
  public Optional<String> findRoomIdByUid(String uid) {
    List<String> ids =
        jdbc.query(
            "SELECT room_id FROM "
                + SCH
                + ".active_rooms WHERE player_uids @> jsonb_build_array(?::text) LIMIT 1",
            (rs, rowNum) -> rs.getString("room_id"),
            uid);
    return ids.stream().findFirst();
  }

  private ActiveRoomRow mapRow(ResultSet rs, int rowNum) throws SQLException {
    List<String> uids = parsePlayerUids(rs.getObject("player_uids"));
    TimestampWrapper tw = timestamps(rs);
    return new ActiveRoomRow(
        rs.getString("room_id"),
        rs.getString("phase"),
        uids,
        rs.getString("invite_code"),
        rs.getString("snapshot_json_text"),
        rs.getString("host_uid"),
        rs.getString("match_mode"),
        rs.getString("ruleset_id"),
        rs.getString("map_id"),
        tw.matchStartedAt(),
        tw.updatedAt(),
        rs.getLong("version"));
  }

  /**
   * PG JDBC may return JSONB column as PGobject/String; normalize to uid list for {@link
   * ActiveRoomRow}.
   */
  private List<String> parsePlayerUids(Object raw) throws SQLException {
    if (raw == null) {
      return new ArrayList<>();
    }
    String s = raw instanceof String ? (String) raw : raw.toString();
    if (s.isBlank()) {
      return new ArrayList<>();
    }
    try {
      return objectMapper.readValue(s, new TypeReference<ArrayList<String>>() {});
    } catch (Exception ex) {
      throw new SQLException("parse player_uids JSON", ex);
    }
  }

  private TimestampWrapper timestamps(ResultSet rs) throws SQLException {
    Timestamp ms = rs.getTimestamp("match_started_at");
    Timestamp ua = rs.getTimestamp("updated_at");
    Instant matchStarted =
        ms == null ? null : Instant.ofEpochMilli(ms.getTime());
    Instant updated = ua == null ? Instant.now() : Instant.ofEpochMilli(ua.getTime());
    return new TimestampWrapper(matchStarted, updated);
  }

  private record TimestampWrapper(Instant matchStartedAt, Instant updatedAt) {}
}
