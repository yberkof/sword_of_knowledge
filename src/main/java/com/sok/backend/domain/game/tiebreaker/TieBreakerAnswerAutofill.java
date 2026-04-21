package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.AnswerMetric;
import com.sok.backend.realtime.match.DuelState;

/**
 * Fills missing estimation answers at deadline (tie-break phase).
 */
public final class TieBreakerAnswerAutofill {

  private TieBreakerAnswerAutofill() {}

  public static void autofillTiebreaker(DuelState duel, int durationMs) {
    if (!duel.tiebreakerAnswers.containsKey(duel.attackerUid)) {
      AnswerMetric a = new AnswerMetric();
      a.uid = duel.attackerUid;
      a.value = 0;
      a.latencyMs = durationMs;
      duel.tiebreakerAnswers.put(a.uid, a);
    }
    if (!"neutral".equals(duel.defenderUid) && !duel.tiebreakerAnswers.containsKey(duel.defenderUid)) {
      AnswerMetric d = new AnswerMetric();
      d.uid = duel.defenderUid;
      d.value = 0;
      d.latencyMs = durationMs;
      duel.tiebreakerAnswers.put(d.uid, d);
    }
  }
}
