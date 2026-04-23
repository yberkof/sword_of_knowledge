package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.service.config.GameRuntimeConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import org.springframework.stereotype.Service;

/**
 * Turn-based memory: 6×6 grid, 18 pairs. Peek period then face-down; match continues same turn;
 * mismatch switches turn; most pairs wins; tie ⇒ defender wins.
 */
@Service
public class MemoryTieBreakInteractionService {

  public enum OutcomeType {
    INVALID_PHASE,
    INVALID_PARTICIPANT,
    INVALID_CELL,
    NOT_YOUR_TURN,
    CELL_ALREADY_MATCHED,
    FIRST_FLIP,
    MATCH_CONTINUE,
    MISMATCH_PASS_TURN,
    MATCH_ATTACKER_WINS,
    MATCH_DEFENDER_WINS,
    MATCH_TIE_DEFENDER_WINS
  }

  public record FlipOutcome(
      OutcomeType type,
      int firstIndex,
      int secondIndex,
      boolean revealedPairIds) {}

  private final Random random = new Random();

  public void startMatch(DuelState duel, TieBreakerRealtimeBridge bridge) {
    duel.tiebreakKind = "memory";
    duel.memorySubPhase = "peek";
    duel.memoryPairByCell = shuffledPairs();
    duel.memoryMatched = new boolean[MemoryTieBreakRules.GRID_CELLS];
    duel.memoryFirstPickIndex = -1;
    duel.memoryAttackerPairs = 0;
    duel.memoryDefenderPairs = 0;
    duel.memoryTurnUid = duel.attackerUid;
    long peekMs = Math.max(3000L, bridge.configuration().getMemoryPeekMs());
    duel.memoryPeekEndsAtMs = System.currentTimeMillis() + peekMs;
    bridge.emitToRoom(
        "tiebreaker_memory_peek",
        CollectionTieBreakPayloadFactory.memoryPeekPayload(bridge.roomId(), duel));
  }

  public void endPeekStartPlay(DuelState duel, TieBreakerRealtimeBridge bridge) {
    if (!"memory".equals(duel.tiebreakKind) || !"peek".equals(duel.memorySubPhase)) {
      return;
    }
    duel.memorySubPhase = "play";
    duel.memoryPeekEndsAtMs = null;
    duel.memoryFirstPickIndex = -1;
    bridge.emitToRoom(
        "tiebreaker_memory_play",
        CollectionTieBreakPayloadFactory.memoryPlayPayload(bridge.roomId(), duel));
  }

  public FlipOutcome flip(DuelState duel, String uid, int cellIndex) {
    if (duel == null || !"memory".equals(duel.tiebreakKind)) {
      return new FlipOutcome(OutcomeType.INVALID_PHASE, -1, -1, false);
    }
    if (!uid.equals(duel.attackerUid) && !uid.equals(duel.defenderUid)) {
      return new FlipOutcome(OutcomeType.INVALID_PARTICIPANT, -1, -1, false);
    }
    if (!"play".equals(duel.memorySubPhase)) {
      return new FlipOutcome(OutcomeType.INVALID_PHASE, -1, -1, false);
    }
    if (!uid.equals(duel.memoryTurnUid)) {
      return new FlipOutcome(OutcomeType.NOT_YOUR_TURN, -1, -1, false);
    }
    if (cellIndex < 0 || cellIndex >= MemoryTieBreakRules.GRID_CELLS) {
      return new FlipOutcome(OutcomeType.INVALID_CELL, -1, -1, false);
    }
    if (duel.memoryMatched[cellIndex]) {
      return new FlipOutcome(OutcomeType.CELL_ALREADY_MATCHED, -1, -1, false);
    }
    if (duel.memoryFirstPickIndex < 0) {
      duel.memoryFirstPickIndex = cellIndex;
      return new FlipOutcome(OutcomeType.FIRST_FLIP, cellIndex, -1, false);
    }
    if (duel.memoryFirstPickIndex == cellIndex) {
      return new FlipOutcome(OutcomeType.INVALID_CELL, -1, -1, false);
    }
    int first = duel.memoryFirstPickIndex;
    duel.memoryFirstPickIndex = -1;
    int pairA = duel.memoryPairByCell[first];
    int pairB = duel.memoryPairByCell[cellIndex];
    if (pairA == pairB) {
      duel.memoryMatched[first] = true;
      duel.memoryMatched[cellIndex] = true;
      if (uid.equals(duel.attackerUid)) {
        duel.memoryAttackerPairs++;
      } else {
        duel.memoryDefenderPairs++;
      }
      int total =
          duel.memoryAttackerPairs + duel.memoryDefenderPairs;
      if (total >= MemoryTieBreakRules.PAIR_TYPES) {
        return finishMatch(duel, first, cellIndex);
      }
      return new FlipOutcome(OutcomeType.MATCH_CONTINUE, first, cellIndex, true);
    }
    duel.memoryTurnUid =
        uid.equals(duel.attackerUid) ? duel.defenderUid : duel.attackerUid;
    return new FlipOutcome(OutcomeType.MISMATCH_PASS_TURN, first, cellIndex, true);
  }

  private FlipOutcome finishMatch(DuelState duel, int a, int b) {
    if (duel.memoryAttackerPairs > duel.memoryDefenderPairs) {
      return new FlipOutcome(OutcomeType.MATCH_ATTACKER_WINS, a, b, true);
    }
    if (duel.memoryDefenderPairs > duel.memoryAttackerPairs) {
      return new FlipOutcome(OutcomeType.MATCH_DEFENDER_WINS, a, b, true);
    }
    return new FlipOutcome(OutcomeType.MATCH_TIE_DEFENDER_WINS, a, b, true);
  }

  private int[] shuffledPairs() {
    ArrayList<Integer> cells = new ArrayList<Integer>();
    for (int p = 0; p < MemoryTieBreakRules.PAIR_TYPES; p++) {
      cells.add(p);
      cells.add(p);
    }
    Collections.shuffle(cells, random);
    int[] board = new int[MemoryTieBreakRules.GRID_CELLS];
    for (int i = 0; i < MemoryTieBreakRules.GRID_CELLS; i++) {
      board[i] = cells.get(i);
    }
    return board;
  }

  /** Build payload-friendly list of pair ids for client (same order as cells 0..35). */
  public static int[] pairLayout(DuelState duel) {
    return duel.memoryPairByCell == null ? null : duel.memoryPairByCell.clone();
  }

  public static boolean[] matchedClone(DuelState duel) {
    return duel.memoryMatched == null ? null : duel.memoryMatched.clone();
  }
}
