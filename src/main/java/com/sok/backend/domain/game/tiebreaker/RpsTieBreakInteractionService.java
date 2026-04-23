package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import java.util.Random;
import org.springframework.stereotype.Service;

/** Rock–paper–scissors tie-break: best of 3 (first to 2). Draw rounds replay without scoring. */
@Service
public class RpsTieBreakInteractionService {

  public static final String ROCK = "rock";
  public static final String PAPER = "paper";
  public static final String SCISSORS = "scissors";

  public enum OutcomeType {
    INVALID_PHASE,
    INVALID_PARTICIPANT,
    INVALID_THROW,
    WAITING_PEER,
    DRAW_ROUND,
    ROUND_ATTACKER_POINT,
    ROUND_DEFENDER_POINT,
    MATCH_ATTACKER_WINS,
    MATCH_DEFENDER_WINS
  }

  public record MoveOutcome(OutcomeType type, int attackerWins, int defenderWins) {

    public static MoveOutcome of(OutcomeType t) {
      return new MoveOutcome(t, 0, 0);
    }

    public static MoveOutcome scores(OutcomeType t, int a, int d) {
      return new MoveOutcome(t, a, d);
    }
  }

  private final Random random = new Random();

  public void startMatch(DuelState duel, TieBreakerRealtimeBridge bridge) {
    duel.tiebreakKind = "rps";
    duel.rpsAttackerWins = 0;
    duel.rpsDefenderWins = 0;
    duel.rpsPendingAttacker = null;
    duel.rpsPendingDefender = null;
    bridge.emitToRoom(
        "tiebreaker_rps_start",
        CollectionTieBreakPayloadFactory.rpsStartPayload(bridge.roomId(), duel));
  }

  public MoveOutcome submitThrow(DuelState duel, String uid, String hand) {
    if (duel == null || !"rps".equals(duel.tiebreakKind)) {
      return MoveOutcome.of(OutcomeType.INVALID_PHASE);
    }
    if (!isParticipant(duel, uid)) {
      return MoveOutcome.of(OutcomeType.INVALID_PARTICIPANT);
    }
    String norm = normalizeHand(hand);
    if (norm == null) {
      return MoveOutcome.of(OutcomeType.INVALID_THROW);
    }
    if (uid.equals(duel.attackerUid)) {
      if (duel.rpsPendingAttacker != null) {
        return MoveOutcome.of(OutcomeType.INVALID_THROW);
      }
      duel.rpsPendingAttacker = norm;
    } else {
      if (duel.rpsPendingDefender != null) {
        return MoveOutcome.of(OutcomeType.INVALID_THROW);
      }
      duel.rpsPendingDefender = norm;
    }
    if (duel.rpsPendingAttacker == null || duel.rpsPendingDefender == null) {
      return MoveOutcome.scores(
          OutcomeType.WAITING_PEER, duel.rpsAttackerWins, duel.rpsDefenderWins);
    }
    return resolveRound(duel);
  }

  private MoveOutcome resolveRound(DuelState duel) {
    String a = duel.rpsPendingAttacker;
    String d = duel.rpsPendingDefender;
    duel.rpsPendingAttacker = null;
    duel.rpsPendingDefender = null;

    if (a.equals(d)) {
      return MoveOutcome.scores(
          OutcomeType.DRAW_ROUND, duel.rpsAttackerWins, duel.rpsDefenderWins);
    }
    boolean attackerWinsRound = beats(a, d);
    if (attackerWinsRound) {
      duel.rpsAttackerWins++;
    } else {
      duel.rpsDefenderWins++;
    }
    if (duel.rpsAttackerWins >= 2) {
      return MoveOutcome.scores(
          OutcomeType.MATCH_ATTACKER_WINS, duel.rpsAttackerWins, duel.rpsDefenderWins);
    }
    if (duel.rpsDefenderWins >= 2) {
      return MoveOutcome.scores(
          OutcomeType.MATCH_DEFENDER_WINS, duel.rpsAttackerWins, duel.rpsDefenderWins);
    }
    return MoveOutcome.scores(
        attackerWinsRound ? OutcomeType.ROUND_ATTACKER_POINT : OutcomeType.ROUND_DEFENDER_POINT,
        duel.rpsAttackerWins,
        duel.rpsDefenderWins);
  }

  /** Random legal throw for autofill / stress tests. */
  public String randomHand() {
    int x = random.nextInt(3);
    return x == 0 ? ROCK : x == 1 ? PAPER : SCISSORS;
  }

  private static boolean beats(String a, String b) {
    return (ROCK.equals(a) && SCISSORS.equals(b))
        || (PAPER.equals(a) && ROCK.equals(b))
        || (SCISSORS.equals(a) && PAPER.equals(b));
  }

  private static boolean isParticipant(DuelState duel, String uid) {
    return uid != null && (uid.equals(duel.attackerUid) || uid.equals(duel.defenderUid));
  }

  private static String normalizeHand(String raw) {
    if (raw == null) return null;
    String t = raw.trim().toLowerCase();
    if (ROCK.equals(t) || PAPER.equals(t) || SCISSORS.equals(t)) return t;
    return null;
  }
}
