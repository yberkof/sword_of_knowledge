package com.sok.backend.realtime.room;

import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RegionState;
import com.sok.backend.realtime.match.RoomState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pure builders for the JSON payloads mirrored to clients via the {@code room_update} event and
 * other room-scoped broadcasts. Kept state-free so payload shape is trivially auditable.
 */
@Component
public class RoomClientSnapshotFactory {

  public Map<String, Object> roomToClient(RoomState room) {
    HashMap<String, Object> payload = new HashMap<>();
    payload.put("id", room.id);
    payload.put("phase", room.phase);
    payload.put("round", room.round);
    payload.put("battleRound", room.round);
    payload.put("mapId", room.mapId);
    payload.put("matchMode", room.matchMode);
    payload.put("rulesetId", room.rulesetId);
    payload.put("currentTurnIndex", room.currentTurnIndex);
    payload.put("hostUid", room.hostUid);
    payload.put("inviteCode", room.inviteCode);
    payload.put("players", playersToClient(room.players));
    payload.put("mapState", regionsToClient(room.regions));
    payload.put("scoreByUid", room.scoreByUid);
    payload.put("claimTurnUid", room.claimTurnUid);
    payload.put("claimPicksLeftByUid", room.claimPicksLeftByUid);
    if (room.activeDuel != null) {
      HashMap<String, Object> duel = new HashMap<>();
      duel.put("attackerUid", room.activeDuel.attackerUid);
      duel.put("defenderUid", room.activeDuel.defenderUid);
      duel.put("targetHexId", room.activeDuel.targetRegionId);
      duel.put(
          "question",
          room.activeDuel.mcqQuestion != null ? room.activeDuel.mcqQuestion.text : null);
      duel.put("tiebreakKind", room.activeDuel.tiebreakKind);
      if ("xo".equals(room.activeDuel.tiebreakKind) && room.activeDuel.xoCells != null) {
        ArrayList<Integer> cells = new ArrayList<>();
        for (int v : room.activeDuel.xoCells) {
          cells.add(v);
        }
        duel.put("xoCells", cells);
        duel.put("xoTurnUid", room.activeDuel.xoTurnUid);
        duel.put("xoReplayCount", room.activeDuel.xoReplayCount);
      }
      payload.put("activeDuel", duel);
    }
    return payload;
  }

  public List<Map<String, Object>> playersToClient(List<PlayerState> players) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (PlayerState p : players) {
      HashMap<String, Object> row = new HashMap<>();
      row.put("uid", p.uid);
      row.put("name", p.name);
      if (p.avatarUrl != null && !p.avatarUrl.isEmpty()) {
        row.put("avatarURL", p.avatarUrl);
      }
      row.put("hp", p.castleHp);
      row.put("color", p.color);
      row.put("isCapitalLost", p.isEliminated);
      row.put("trophies", p.trophies);
      row.put("eliminatedAt", p.eliminatedAt);
      row.put("castleRegionId", p.castleRegionId);
      row.put("online", p.online);
      row.put("teamId", p.teamId);
      out.add(row);
    }
    return out;
  }

  public List<Map<String, Object>> regionsToClient(Map<Integer, RegionState> regions) {
    List<Map<String, Object>> out = new ArrayList<>();
    List<Integer> ids = new ArrayList<>(regions.keySet());
    Collections.sort(ids);
    for (Integer id : ids) {
      RegionState h = regions.get(id);
      HashMap<String, Object> row = new HashMap<>();
      row.put("id", h.id);
      row.put("ownerUid", h.ownerUid);
      row.put("isCapital", h.isCastle);
      row.put("isShielded", h.isShielded);
      row.put("type", h.type);
      row.put("points", h.points);
      row.put("neighbors", h.neighbors);
      out.add(row);
    }
    return out;
  }

  /** Count of not-eliminated, currently-connected players; floors at 1 to avoid div-by-zero. */
  public int onlinePlayerCount(RoomState room) {
    int count = 0;
    for (PlayerState p : room.players) {
      if (!p.isEliminated && p.online) count++;
    }
    return Math.max(1, count);
  }

  /** Point value for a region; treats missing or non-positive values as 1. */
  public int pointValue(RoomState room, int regionId) {
    RegionState r = room.regions.get(regionId);
    if (r == null) return 1;
    return r.points <= 0 ? 1 : r.points;
  }

  /** Minimal JSON used by the Redis snapshot publisher for out-of-band discovery. */
  public String minimalSnapshotJson(RoomState room) {
    return "{\"id\":\""
        + room.id
        + "\",\"phase\":\""
        + room.phase
        + "\",\"players\":"
        + room.players.size()
        + ",\"round\":"
        + room.round
        + "}";
  }

  public static HashMap<String, Object> mapOf(String k1, Object v1) {
    HashMap<String, Object> map = new HashMap<>();
    map.put(k1, v1);
    return map;
  }

  public static HashMap<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
    HashMap<String, Object> map = new HashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }
}
