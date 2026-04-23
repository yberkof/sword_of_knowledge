package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client payloads for collection lobby and sub-minigames. */
public final class CollectionTieBreakPayloadFactory {

  private CollectionTieBreakPayloadFactory() {}

  public static Map<String, Object> collectionStartPayload(
      String roomId, DuelState duel, long pickMs, long serverNowMs) {
    HashMap<String, Object> m = new HashMap<>();
    m.put("roomId", roomId);
    m.put("options", new ArrayList<>(CollectionMinigameIds.VOTE_OPTIONS));
    m.put("pickDeadlineMs", pickMs);
    m.put("serverNowMs", serverNowMs);
    m.put("pickEndsAtMs", serverNowMs + pickMs);
    m.put("attackerUid", duel.attackerUid);
    m.put("defenderUid", duel.defenderUid);
    return m;
  }

  public static Map<String, Object> collectionResolvedPayload(
      String roomId, DuelState duel, String resolvedGame) {
    HashMap<String, Object> m = new HashMap<>();
    m.put("roomId", roomId);
    m.put("resolvedGame", resolvedGame);
    m.put("attackerPick", duel.collectionAttackerPick);
    m.put("defenderPick", duel.collectionDefenderPick);
    return m;
  }

  public static Map<String, Object> rpsStartPayload(String roomId, DuelState duel) {
    HashMap<String, Object> m = new HashMap<>();
    m.put("roomId", roomId);
    m.put("attackerUid", duel.attackerUid);
    m.put("defenderUid", duel.defenderUid);
    m.put("attackerWins", duel.rpsAttackerWins);
    m.put("defenderWins", duel.rpsDefenderWins);
    return m;
  }

  public static Map<String, Object> rpsRoundPayload(String roomId, DuelState duel, String outcomeType) {
    HashMap<String, Object> m = new HashMap<>();
    m.put("roomId", roomId);
    m.put("outcomeType", outcomeType);
    m.put("attackerWins", duel.rpsAttackerWins);
    m.put("defenderWins", duel.rpsDefenderWins);
    return m;
  }

  public static Map<String, Object> rhythmRoundPayload(String roomId, DuelState duel) {
    HashMap<String, Object> m = new HashMap<>();
    m.put("roomId", roomId);
    m.put("round", duel.rhythmRound);
    ArrayList<Integer> seq = new ArrayList<>();
    if (duel.rhythmSequence != null) {
      for (int v : duel.rhythmSequence) seq.add(v);
    }
    m.put("sequence", seq);
    m.put("deadlineMs", duel.rhythmRoundDeadlineAtMs);
    return m;
  }

  public static Map<String, Object> memoryPeekPayload(String roomId, DuelState duel) {
    HashMap<String, Object> m = new HashMap<>();
    m.put("roomId", roomId);
    m.put("subPhase", duel.memorySubPhase);
    m.put("peekEndsAtMs", duel.memoryPeekEndsAtMs);
    m.put("gridRows", MemoryTieBreakRules.GRID_ROWS);
    m.put("gridCols", MemoryTieBreakRules.GRID_COLS);
    if (duel.memoryPairByCell != null) {
      ArrayList<Integer> pairs = new ArrayList<>();
      for (int v : duel.memoryPairByCell) pairs.add(v);
      m.put("pairByCell", pairs);
    }
    m.put("turnUid", duel.memoryTurnUid);
    return m;
  }

  public static Map<String, Object> memoryPlayPayload(String roomId, DuelState duel) {
    HashMap<String, Object> m = new HashMap<>();
    m.put("roomId", roomId);
    m.put("subPhase", duel.memorySubPhase);
    m.put("turnUid", duel.memoryTurnUid);
    m.put("attackerPairs", duel.memoryAttackerPairs);
    m.put("defenderPairs", duel.memoryDefenderPairs);
    m.put("gridRows", MemoryTieBreakRules.GRID_ROWS);
    m.put("gridCols", MemoryTieBreakRules.GRID_COLS);
    if (duel.memoryPairByCell != null) {
      ArrayList<Integer> pairs = new ArrayList<>();
      for (int v : duel.memoryPairByCell) pairs.add(v);
      m.put("pairByCell", pairs);
    }
    return m;
  }

  public static Map<String, Object> memoryFlipPayload(
      String roomId,
      DuelState duel,
      String outcomeType,
      int firstIdx,
      int secondIdx,
      boolean showPairIds) {
    HashMap<String, Object> m = new HashMap<>();
    m.put("roomId", roomId);
    m.put("outcomeType", outcomeType);
    m.put("firstIndex", firstIdx);
    m.put("secondIndex", secondIdx < 0 ? null : secondIdx);
    m.put("turnUid", duel.memoryTurnUid);
    m.put("attackerPairs", duel.memoryAttackerPairs);
    m.put("defenderPairs", duel.memoryDefenderPairs);
    if (showPairIds && duel.memoryPairByCell != null && firstIdx >= 0 && secondIdx >= 0) {
      m.put("pairFirst", duel.memoryPairByCell[firstIdx]);
      m.put("pairSecond", duel.memoryPairByCell[secondIdx]);
    }
    return m;
  }
}
