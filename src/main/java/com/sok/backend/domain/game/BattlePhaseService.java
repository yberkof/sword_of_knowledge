package com.sok.backend.domain.game;

import com.sok.backend.domain.game.engine.TurnOutcome;
import org.springframework.stereotype.Service;

@Service
public class BattlePhaseService {
  public enum DuelOutcome {
    ATTACKER_WINS,
    DEFENDER_WINS,
    TIE
  }

  public DuelOutcome resolveMcq(
      boolean attackerCorrect,
      boolean defenderCorrect,
      long attackerLatencyMs,
      long defenderLatencyMs,
      boolean hasHumanDefender) {
    if (!hasHumanDefender) {
      return attackerCorrect ? DuelOutcome.ATTACKER_WINS : DuelOutcome.DEFENDER_WINS;
    }
    if (attackerCorrect && defenderCorrect && attackerLatencyMs == defenderLatencyMs) {
      return DuelOutcome.TIE;
    }
    if (attackerCorrect && !defenderCorrect) return DuelOutcome.ATTACKER_WINS;
    if (!attackerCorrect && defenderCorrect) return DuelOutcome.DEFENDER_WINS;
    if (!attackerCorrect && !defenderCorrect) return DuelOutcome.DEFENDER_WINS;
    return attackerLatencyMs <= defenderLatencyMs
        ? DuelOutcome.ATTACKER_WINS
        : DuelOutcome.DEFENDER_WINS;
  }

  /**
   * Engine-facing variant of {@link #resolveMcq}. Maps the legacy {@link DuelOutcome} onto the
   * sealed {@link TurnOutcome} so phase code can pattern-match on the three first-class
   * situations (expanding / failed_attack / tie_breaking) without re-deriving them.
   */
  public TurnOutcome resolveMcqAsOutcome(
      String attackerUid,
      String defenderUid,
      int targetRegionId,
      int hpDamageOnWin,
      boolean attackerCorrect,
      boolean defenderCorrect,
      long attackerLatencyMs,
      long defenderLatencyMs,
      boolean hasHumanDefender,
      String tieBreakerStrategyId) {
    DuelOutcome legacy =
        resolveMcq(attackerCorrect, defenderCorrect, attackerLatencyMs, defenderLatencyMs, hasHumanDefender);
    return switch (legacy) {
      case ATTACKER_WINS -> new TurnOutcome.Expanding(attackerUid, targetRegionId, hpDamageOnWin);
      case DEFENDER_WINS -> new TurnOutcome.FailedAttack(attackerUid, defenderUid);
      case TIE -> new TurnOutcome.TieBreaking("mcq_speed_tie", tieBreakerStrategyId);
    };
  }
}
