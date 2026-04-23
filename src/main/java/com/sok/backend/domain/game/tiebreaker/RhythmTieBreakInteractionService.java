package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.service.config.GameRuntimeConfig;
import java.util.Arrays;
import java.util.Random;
import org.springframework.stereotype.Service;

/**
 * Simon-style rhythm tie-break: shared color sequence grows each round (length 4 + roundIndex).
 * Both players must replay; both wrong same round ⇒ defender wins.
 */
@Service
public class RhythmTieBreakInteractionService {

  public static final int COLOR_COUNT = 4;

  public enum OutcomeType {
    INVALID_PHASE,
    INVALID_PARTICIPANT,
    INVALID_INPUT,
    WAITING_PEER,
    CONTINUE_NEXT_ROUND,
    ATTACKER_WINS,
    DEFENDER_WINS,
    BOTH_FAIL_DEFENDER_WINS
  }

  public record MoveOutcome(OutcomeType type) {}

  private final Random random = new Random();

  public void startMatch(DuelState duel, TieBreakerRealtimeBridge bridge) {
    duel.tiebreakKind = "rhythm";
    duel.rhythmRound = 0;
    duel.rhythmSequence = generateSequence(sequenceLengthForRound(0));
    duel.rhythmPendingAttackerInput = null;
    duel.rhythmPendingDefenderInput = null;
    deadlineForRound(duel, bridge.configuration());
    bridge.emitToRoom(
        "tiebreaker_rhythm_round",
        CollectionTieBreakPayloadFactory.rhythmRoundPayload(bridge.roomId(), duel));
  }

  public static int sequenceLengthForRound(int round) {
    return 4 + round;
  }

  public MoveOutcome submitReplay(DuelState duel, String uid, int[] inputs, TieBreakerRealtimeBridge bridge) {
    if (duel == null || !"rhythm".equals(duel.tiebreakKind)) {
      return new MoveOutcome(OutcomeType.INVALID_PHASE);
    }
    if (!uid.equals(duel.attackerUid) && !uid.equals(duel.defenderUid)) {
      return new MoveOutcome(OutcomeType.INVALID_PARTICIPANT);
    }
    if (duel.rhythmSequence == null
        || inputs == null
        || inputs.length != duel.rhythmSequence.length) {
      return new MoveOutcome(OutcomeType.INVALID_INPUT);
    }
    String csv = joinInputs(inputs);
    if (uid.equals(duel.attackerUid)) {
      if (duel.rhythmPendingAttackerInput != null) {
        return new MoveOutcome(OutcomeType.INVALID_INPUT);
      }
      duel.rhythmPendingAttackerInput = csv;
    } else {
      if (duel.rhythmPendingDefenderInput != null) {
        return new MoveOutcome(OutcomeType.INVALID_INPUT);
      }
      duel.rhythmPendingDefenderInput = csv;
    }
    if (duel.rhythmPendingAttackerInput == null || duel.rhythmPendingDefenderInput == null) {
      return new MoveOutcome(OutcomeType.WAITING_PEER);
    }
    return evaluateRound(duel, bridge);
  }

  private MoveOutcome evaluateRound(DuelState duel, TieBreakerRealtimeBridge bridge) {
    boolean attOk =
        csvMatches(duel.rhythmPendingAttackerInput, duel.rhythmSequence);
    boolean defOk =
        csvMatches(duel.rhythmPendingDefenderInput, duel.rhythmSequence);
    duel.rhythmPendingAttackerInput = null;
    duel.rhythmPendingDefenderInput = null;

    if (!attOk && !defOk) {
      return new MoveOutcome(OutcomeType.BOTH_FAIL_DEFENDER_WINS);
    }
    if (attOk && !defOk) {
      return new MoveOutcome(OutcomeType.ATTACKER_WINS);
    }
    if (!attOk) {
      return new MoveOutcome(OutcomeType.DEFENDER_WINS);
    }
    duel.rhythmRound++;
    duel.rhythmSequence = generateSequence(sequenceLengthForRound(duel.rhythmRound));
    deadlineForRound(duel, bridge.configuration());
    bridge.emitToRoom(
        "tiebreaker_rhythm_round",
        CollectionTieBreakPayloadFactory.rhythmRoundPayload(bridge.roomId(), duel));
    return new MoveOutcome(OutcomeType.CONTINUE_NEXT_ROUND);
  }

  public int[] generateSequence(int len) {
    int[] s = new int[len];
    for (int i = 0; i < len; i++) {
      s[i] = random.nextInt(COLOR_COUNT);
    }
    return s;
  }

  private void deadlineForRound(DuelState duel, GameRuntimeConfig cfg) {
    long ms =
        Math.max(
            3000L,
            cfg.getRhythmTimeoutBaseMs()
                + (long) duel.rhythmRound * cfg.getRhythmTimeoutExtraPerRoundMs());
    duel.rhythmRoundDeadlineAtMs = System.currentTimeMillis() + ms;
  }

  private static boolean csvMatches(String csv, int[] seq) {
    int[] parsed = parseCsv(csv);
    return Arrays.equals(parsed, seq);
  }

  static int[] parseCsv(String csv) {
    if (csv == null || csv.isEmpty()) return new int[0];
    String[] parts = csv.split(",");
    int[] out = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
      out[i] = Integer.parseInt(parts[i].trim());
    }
    return out;
  }

  static String joinInputs(int[] inputs) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < inputs.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(inputs[i]);
    }
    return sb.toString();
  }

  /** Used when deadline hits: missing submission counts as failure (wrong sequence). */
  public MoveOutcome forceEvaluateIfReady(DuelState duel, TieBreakerRealtimeBridge bridge) {
    if (!"rhythm".equals(duel.tiebreakKind) || duel.rhythmSequence == null) {
      return new MoveOutcome(OutcomeType.INVALID_PHASE);
    }
    if (duel.rhythmPendingAttackerInput != null && duel.rhythmPendingDefenderInput != null) {
      return evaluateRound(duel, bridge);
    }
    if (duel.rhythmPendingAttackerInput == null) {
      duel.rhythmPendingAttackerInput = "";
    }
    if (duel.rhythmPendingDefenderInput == null) {
      duel.rhythmPendingDefenderInput = "";
    }
    return evaluateRound(duel, bridge);
  }
}
