package com.sok.backend.domain.game.tiebreaker;

/**
 * Canonical wire / config ids for MCQ speed-tie handling and tie-break phases. Open for extension:
 * register new ids in {@link TieBreakerAttackPhaseStrategyRegistry}.
 */
public final class TieBreakerModeIds {

  private TieBreakerModeIds() {}

  public static final String NUMERIC_CLOSEST = "numeric_closest";
  public static final String MCQ_RETRY = "mcq_retry";
  public static final String ATTACKER_ADVANTAGE = "attacker_advantage";
  public static final String MINIGAME_XO = "minigame_xo";

  /**
   * Normalizes user / config input to a known id; unknown values map to {@link #NUMERIC_CLOSEST}.
   */
  public static String normalize(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
      return NUMERIC_CLOSEST;
    }
    String t = raw.trim().toLowerCase();
    if (MCQ_RETRY.equals(t)) return MCQ_RETRY;
    if (ATTACKER_ADVANTAGE.equals(t)) return ATTACKER_ADVANTAGE;
    if (MINIGAME_XO.equals(t)) return MINIGAME_XO;
    return NUMERIC_CLOSEST;
  }
}
