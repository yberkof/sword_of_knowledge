package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.AnswerMetric;
import com.sok.backend.realtime.match.DuelState;

/**
 * Pure closest-guess resolution for numeric tie-break rounds.
 */
public final class NumericTiebreakEvaluator {

  private NumericTiebreakEvaluator() {}

  /** {@code true} if attacker wins the hex/castle exchange. */
  public static boolean attackerWinsClosest(
      DuelState duel, AnswerMetric attacker, AnswerMetric defender, boolean defenderIsNeutral) {
    if (defenderIsNeutral) {
      return true;
    }
    int target = duel.numericQuestion.answer;
    int ad = Math.abs(attacker.value - target);
    int dd = Math.abs(defender.value - target);
    if (ad != dd) return ad < dd;
    return attacker.latencyMs <= defender.latencyMs;
  }
}
