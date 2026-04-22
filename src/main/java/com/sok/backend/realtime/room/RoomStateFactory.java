package com.sok.backend.realtime.room;

import com.sok.backend.realtime.match.RegionState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.service.config.GameRuntimeConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Pure factory for {@link RoomState} and {@link RegionState} graphs from runtime config. */
@Component
public class RoomStateFactory {

  public static final String MODE_FFA = "ffa";
  public static final String MODE_TEAMS_2V2 = "teams_2v2";

  public RoomState newRoomStateWithDefaults(GameRuntimeConfig cfg) {
    RoomState room = new RoomState();
    room.mapId = cfg.getDefaultMapId();
    room.matchMode = normalizeMatchMode(cfg.getDefaultMatchMode(), MODE_FFA);
    room.rulesetId = cfg.getDefaultRulesetId();
    return room;
  }

  public Map<Integer, RegionState> buildRegionsFromConfig(GameRuntimeConfig cfg) {
    HashMap<Integer, RegionState> out = new HashMap<>();
    for (Map.Entry<String, List<Integer>> e : cfg.getNeighbors().entrySet()) {
      int id = Integer.parseInt(e.getKey());
      RegionState r = new RegionState();
      r.id = id;
      r.ownerUid = null;
      r.isCastle = false;
      r.points = cfg.getRegionPoints().getOrDefault(e.getKey(), 1);
      r.neighbors = new ArrayList<>(e.getValue());
      out.put(id, r);
    }
    return out;
  }

  /** Accepts optional requested mode; falls back to cfg default; clamps to known modes. */
  public static String normalizeMatchMode(String raw, String fallback) {
    String base =
        (fallback == null || fallback.trim().isEmpty())
            ? MODE_FFA
            : fallback.trim().toLowerCase();
    if (raw == null || raw.trim().isEmpty()) {
      return base;
    }
    String t = raw.trim().toLowerCase();
    if (MODE_TEAMS_2V2.equals(t)) {
      return MODE_TEAMS_2V2;
    }
    return MODE_FFA;
  }
}
