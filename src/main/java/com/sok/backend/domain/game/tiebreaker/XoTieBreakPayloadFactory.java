package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Builds client payloads for X-O tie-break events. */
public final class XoTieBreakPayloadFactory {

  private XoTieBreakPayloadFactory() {}

  public static Map<String, Object> xoPayload(String roomId, DuelState duel) {
    HashMap<String, Object> m = new HashMap<String, Object>();
    m.put("roomId", roomId);
    m.put("attackerUid", duel.attackerUid);
    m.put("defenderUid", duel.defenderUid);
    m.put("xoTurnUid", duel.xoTurnUid);
    ArrayList<Integer> cells = new ArrayList<Integer>();
    if (duel.xoCells != null) {
      for (int v : duel.xoCells) {
        cells.add(v);
      }
    }
    m.put("xoCells", cells);
    return m;
  }

  public static Map<String, Object> replayPayload(String roomId, int replayNumber) {
    HashMap<String, Object> replay = new HashMap<String, Object>();
    replay.put("roomId", roomId);
    replay.put("replayNumber", replayNumber);
    return replay;
  }
}
