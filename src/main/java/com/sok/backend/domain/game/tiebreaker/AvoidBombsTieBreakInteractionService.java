package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import org.springframework.stereotype.Service;

/**
 * Applies one avoid-bombs interaction (bomb placement or cell open) and classifies the outcome.
 * Pure logic — does not perform I/O. The gateway turns {@link MoveOutcome}s into socket emits and
 * {@code finishBattle} calls.
 *
 * <p>Interaction model:
 * <ul>
 *   <li>During {@code placement}, each participant submits their own 3 bomb indices via
 *       {@link #placeBombs(DuelState, String, Collection)}.</li>
 *   <li>During {@code opening}, the current-turn player opens one cell on the opponent's board
 *       via {@link #openCell(DuelState, String, int)}. The opener loses when their own
 *       bomb-opened counter reaches {@link AvoidBombsBoardRules#LOSE_THRESHOLD}.</li>
 * </ul>
 */
@Service
public class AvoidBombsTieBreakInteractionService {

  private final Random random;

  public AvoidBombsTieBreakInteractionService() {
    this(new Random());
  }

  /** Package-private test hook: inject a deterministic {@link Random}. */
  AvoidBombsTieBreakInteractionService(Random random) {
    this.random = random;
  }

  public enum OutcomeType {
    INVALID_PHASE,
    INVALID_PARTICIPANT,
    INVALID_LAYOUT,
    INVALID_DUPLICATE_PLACEMENT,
    INVALID_NOT_YOUR_TURN,
    INVALID_CELL,
    INVALID_ALREADY_OPENED,
    PLACEMENT_ACCEPTED,
    PLACEMENT_BOTH_READY,
    REVEAL_SAFE,
    REVEAL_BOMB,
    ATTACKER_WIN,
    DEFENDER_WIN
  }

  /**
   * Snapshot of a resolved interaction — callers ({@code SocketGateway}) translate this into
   * socket emits and, where appropriate, a call to {@code finishBattle}.
   */
  public record MoveOutcome(
      OutcomeType outcomeType,
      String openerUid,
      String targetUid,
      int cellIndex,
      boolean isBomb,
      int hitsByOpener,
      String nextTurnUid) {

    public static MoveOutcome simple(OutcomeType type) {
      return new MoveOutcome(type, null, null, -1, false, 0, null);
    }
  }

  // -------- placement --------

  public MoveOutcome placeBombs(DuelState duel, String uid, Collection<Integer> bombCells) {
    if (duel == null
        || !"avoid_bombs".equals(duel.tiebreakKind)
        || !"placement".equals(duel.avoidBombsSubPhase)) {
      return MoveOutcome.simple(OutcomeType.INVALID_PHASE);
    }
    if (!isParticipant(duel, uid)) {
      return MoveOutcome.simple(OutcomeType.INVALID_PARTICIPANT);
    }
    Boolean already = duel.avoidBombsPlaced.get(uid);
    if (already != null && already) {
      return MoveOutcome.simple(OutcomeType.INVALID_DUPLICATE_PLACEMENT);
    }
    int[] layout = AvoidBombsBoardRules.layoutFrom(bombCells);
    if (layout == null) {
      return MoveOutcome.simple(OutcomeType.INVALID_LAYOUT);
    }
    duel.avoidBombsBoards.put(uid, layout);
    duel.avoidBombsPlaced.put(uid, Boolean.TRUE);
    duel.avoidBombsOpened.computeIfAbsent(uid, k -> AvoidBombsBoardRules.emptyBoard());
    duel.avoidBombsHitsBy.putIfAbsent(uid, 0);
    if (bothPlaced(duel)) {
      enterOpeningPhase(duel);
      return MoveOutcome.simple(OutcomeType.PLACEMENT_BOTH_READY);
    }
    return MoveOutcome.simple(OutcomeType.PLACEMENT_ACCEPTED);
  }

  /**
   * Auto-fills missing placements with random bombs (used when the placement timer expires) and
   * transitions the duel into the opening sub-phase. Safe to call repeatedly; returns the post-
   * condition outcome (always {@link OutcomeType#PLACEMENT_BOTH_READY} unless the duel is in the
   * wrong state).
   */
  public MoveOutcome autofillPlacementsAndStart(DuelState duel) {
    if (duel == null
        || !"avoid_bombs".equals(duel.tiebreakKind)
        || !"placement".equals(duel.avoidBombsSubPhase)) {
      return MoveOutcome.simple(OutcomeType.INVALID_PHASE);
    }
    autofillIfMissing(duel, duel.attackerUid);
    autofillIfMissing(duel, duel.defenderUid);
    enterOpeningPhase(duel);
    return MoveOutcome.simple(OutcomeType.PLACEMENT_BOTH_READY);
  }

  // -------- opening --------

  public MoveOutcome openCell(DuelState duel, String uid, int cellIndex) {
    if (duel == null
        || !"avoid_bombs".equals(duel.tiebreakKind)
        || !"opening".equals(duel.avoidBombsSubPhase)) {
      return MoveOutcome.simple(OutcomeType.INVALID_PHASE);
    }
    if (!isParticipant(duel, uid)) {
      return MoveOutcome.simple(OutcomeType.INVALID_PARTICIPANT);
    }
    if (!uid.equals(duel.avoidBombsTurnUid)) {
      return MoveOutcome.simple(OutcomeType.INVALID_NOT_YOUR_TURN);
    }
    if (cellIndex < 0 || cellIndex >= AvoidBombsBoardRules.GRID_SIZE) {
      return MoveOutcome.simple(OutcomeType.INVALID_CELL);
    }
    String targetUid = opponentOf(duel, uid);
    int[] targetBoard = duel.avoidBombsBoards.get(targetUid);
    int[] openedMask = duel.avoidBombsOpened.get(targetUid);
    if (openedMask == null) {
      openedMask = AvoidBombsBoardRules.emptyBoard();
      duel.avoidBombsOpened.put(targetUid, openedMask);
    }
    if (AvoidBombsBoardRules.cellAlreadyOpened(openedMask, cellIndex)) {
      return MoveOutcome.simple(OutcomeType.INVALID_ALREADY_OPENED);
    }
    boolean isBomb = AvoidBombsBoardRules.isBomb(targetBoard, cellIndex);
    AvoidBombsBoardRules.markOpened(openedMask, cellIndex);

    int hitsByOpener = duel.avoidBombsHitsBy.getOrDefault(uid, 0);
    if (isBomb) {
      hitsByOpener++;
      duel.avoidBombsHitsBy.put(uid, hitsByOpener);
      if (AvoidBombsBoardRules.loseReached(hitsByOpener)) {
        // Opener hit their 3rd bomb → opener loses, opponent wins the duel.
        OutcomeType winner =
            uid.equals(duel.attackerUid) ? OutcomeType.DEFENDER_WIN : OutcomeType.ATTACKER_WIN;
        return new MoveOutcome(winner, uid, targetUid, cellIndex, true, hitsByOpener, null);
      }
    }
    String nextTurnUid = opponentOf(duel, uid);
    duel.avoidBombsTurnUid = nextTurnUid;
    return new MoveOutcome(
        isBomb ? OutcomeType.REVEAL_BOMB : OutcomeType.REVEAL_SAFE,
        uid,
        targetUid,
        cellIndex,
        isBomb,
        hitsByOpener,
        nextTurnUid);
  }

  // -------- helpers --------

  private boolean isParticipant(DuelState duel, String uid) {
    return uid != null && (uid.equals(duel.attackerUid) || uid.equals(duel.defenderUid));
  }

  private static String opponentOf(DuelState duel, String uid) {
    if (uid == null) return null;
    return uid.equals(duel.attackerUid) ? duel.defenderUid : duel.attackerUid;
  }

  private static boolean bothPlaced(DuelState duel) {
    return Boolean.TRUE.equals(duel.avoidBombsPlaced.get(duel.attackerUid))
        && Boolean.TRUE.equals(duel.avoidBombsPlaced.get(duel.defenderUid));
  }

  private void autofillIfMissing(DuelState duel, String uid) {
    if (uid == null) return;
    if (Boolean.TRUE.equals(duel.avoidBombsPlaced.get(uid))) return;
    duel.avoidBombsBoards.put(uid, AvoidBombsBoardRules.randomLayout(random));
    duel.avoidBombsPlaced.put(uid, Boolean.TRUE);
    duel.avoidBombsOpened.putIfAbsent(uid, AvoidBombsBoardRules.emptyBoard());
    duel.avoidBombsHitsBy.putIfAbsent(uid, 0);
  }

  private static void enterOpeningPhase(DuelState duel) {
    duel.avoidBombsSubPhase = "opening";
    duel.avoidBombsTurnUid = duel.attackerUid;
    // Ensure opened masks + counters exist for both sides.
    duel.avoidBombsOpened.computeIfAbsent(duel.attackerUid, k -> AvoidBombsBoardRules.emptyBoard());
    duel.avoidBombsOpened.computeIfAbsent(duel.defenderUid, k -> AvoidBombsBoardRules.emptyBoard());
    duel.avoidBombsHitsBy.putIfAbsent(duel.attackerUid, 0);
    duel.avoidBombsHitsBy.putIfAbsent(duel.defenderUid, 0);
  }

  /** Utility for tests / debugging: the two participant uids, attacker first. */
  public static String[] participants(DuelState duel) {
    if (duel == null) return new String[0];
    return new String[] {duel.attackerUid, duel.defenderUid};
  }

  /** Utility for tests / debugging: shallow copy of the board for assertions. */
  public static int[] boardCopy(DuelState duel, String uid) {
    int[] b = duel == null ? null : duel.avoidBombsBoards.get(uid);
    return b == null ? new int[0] : Arrays.copyOf(b, b.length);
  }
}
