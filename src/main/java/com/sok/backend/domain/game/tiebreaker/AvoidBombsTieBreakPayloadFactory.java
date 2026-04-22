package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds client-facing payloads for the avoid-bombs tie-break minigame. */
public final class AvoidBombsTieBreakPayloadFactory {

  private AvoidBombsTieBreakPayloadFactory() {}

  /** Broadcast start payload — does NOT include any bomb layout. */
  public static Map<String, Object> startPayload(String roomId, DuelState duel, long placementMs) {
    HashMap<String, Object> m = new HashMap<String, Object>();
    m.put("roomId", roomId);
    m.put("attackerUid", duel.attackerUid);
    m.put("defenderUid", duel.defenderUid);
    m.put("gridSize", AvoidBombsBoardRules.GRID_SIZE);
    m.put("bombCount", AvoidBombsBoardRules.BOMB_COUNT);
    m.put("placementMs", placementMs);
    m.put("subPhase", "placement");
    return m;
  }

  /**
   * Private ack emitted to the placer only (echoes their own bomb layout so the client can render
   * it deterministically).
   */
  public static Map<String, Object> placementAckPayload(
      String roomId, String uid, int[] board) {
    HashMap<String, Object> m = new HashMap<String, Object>();
    m.put("roomId", roomId);
    m.put("uid", uid);
    m.put("bombCells", asList(AvoidBombsBoardRules.bombIndices(board)));
    return m;
  }

  /** Broadcast when both placements are locked-in and the opening sub-phase starts. */
  public static Map<String, Object> readyPayload(String roomId, DuelState duel) {
    HashMap<String, Object> m = new HashMap<String, Object>();
    m.put("roomId", roomId);
    m.put("subPhase", "opening");
    m.put("turnUid", duel.avoidBombsTurnUid);
    m.put("openedByUid", snapshotOpened(duel));
    m.put("hitsBy", snapshotHits(duel));
    return m;
  }

  /** Broadcast after every cell opening in the opening sub-phase. */
  public static Map<String, Object> revealPayload(
      String roomId,
      DuelState duel,
      String openerUid,
      String targetUid,
      int cellIndex,
      boolean isBomb,
      String nextTurnUid) {
    HashMap<String, Object> m = new HashMap<String, Object>();
    m.put("roomId", roomId);
    m.put("openerUid", openerUid);
    m.put("targetUid", targetUid);
    m.put("cellIndex", cellIndex);
    m.put("isBomb", isBomb);
    m.put("turnUid", nextTurnUid);
    m.put("hitsBy", snapshotHits(duel));
    m.put("openedByUid", snapshotOpened(duel));
    return m;
  }

  /**
   * Post-duel reveal — includes the full bomb layout of both players so the UI can render a final
   * board state.
   */
  public static Map<String, Object> revealAllPayload(String roomId, DuelState duel) {
    HashMap<String, Object> m = new HashMap<String, Object>();
    m.put("roomId", roomId);
    HashMap<String, List<Integer>> bombs = new HashMap<String, List<Integer>>();
    bombs.put(
        duel.attackerUid, asList(AvoidBombsBoardRules.bombIndices(duel.avoidBombsBoards.get(duel.attackerUid))));
    bombs.put(
        duel.defenderUid, asList(AvoidBombsBoardRules.bombIndices(duel.avoidBombsBoards.get(duel.defenderUid))));
    m.put("bombsByUid", bombs);
    m.put("hitsBy", snapshotHits(duel));
    m.put("openedByUid", snapshotOpened(duel));
    return m;
  }

  // -------- helpers --------

  private static Map<String, List<Integer>> snapshotOpened(DuelState duel) {
    HashMap<String, List<Integer>> out = new HashMap<String, List<Integer>>();
    for (Map.Entry<String, int[]> e : duel.avoidBombsOpened.entrySet()) {
      // Wire format: opened cell indices (not the raw 9-length 0/1 mask as nine integers).
      out.put(e.getKey(), AvoidBombsBoardRules.openedCellIndices(e.getValue()));
    }
    return out;
  }

  private static Map<String, Integer> snapshotHits(DuelState duel) {
    return new HashMap<String, Integer>(duel.avoidBombsHitsBy);
  }

  private static List<Integer> asList(int[] arr) {
    ArrayList<Integer> list = new ArrayList<Integer>(arr == null ? 0 : arr.length);
    if (arr != null) {
      for (int v : arr) list.add(v);
    }
    return list;
  }
}
