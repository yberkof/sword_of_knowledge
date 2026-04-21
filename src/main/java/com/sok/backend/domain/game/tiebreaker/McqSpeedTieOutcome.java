package com.sok.backend.domain.game.tiebreaker;

/**
 * Result of resolving an MCQ duel where both answers are correct with identical latency.
 */
public record McqSpeedTieOutcome(
    Action action,
    boolean attackerWinsIfFinishing,
    /** Applied when {@link Action#RESTART_MCQ_DUEL}. */
    int nextMcqRetryCount,
    /** Applied when {@link Action#ENTER_POST_MCQ_TIE_ATTACK} before opening tie-break phase. */
    boolean resetMcqRetriesBeforeTieAttack,
    String tieBreakOverrideOrNull) {

  public enum Action {
    FINISH_BATTLE,
    RESTART_MCQ_DUEL,
    ENTER_POST_MCQ_TIE_ATTACK
  }

  public static McqSpeedTieOutcome finish(boolean attackerWins) {
    return new McqSpeedTieOutcome(Action.FINISH_BATTLE, attackerWins, 0, false, null);
  }

  public static McqSpeedTieOutcome retryMcq(int nextMcqRetryCount) {
    return new McqSpeedTieOutcome(Action.RESTART_MCQ_DUEL, false, nextMcqRetryCount, false, null);
  }

  /** @param tieBreakOverrideOrNull forces numeric estimation after mcq_retry exhaustion when non-null */
  public static McqSpeedTieOutcome enterTieAttack(boolean resetRetries, String tieBreakOverrideOrNull) {
    return new McqSpeedTieOutcome(
        Action.ENTER_POST_MCQ_TIE_ATTACK, false, 0, resetRetries, tieBreakOverrideOrNull);
  }
}
