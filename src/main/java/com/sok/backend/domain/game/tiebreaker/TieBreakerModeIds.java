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
  public static final String MINIGAME_AVOID_BOMBS = "minigame_avoid_bombs";
  /** Lobby that lets both players vote, then plays one sub-minigame. */
  public static final String MINIGAME_COLLECTION = "minigame_collection";

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
    if (MINIGAME_AVOID_BOMBS.equals(t)) return MINIGAME_AVOID_BOMBS;
    if (MINIGAME_COLLECTION.equals(t)) return MINIGAME_COLLECTION;
    return NUMERIC_CLOSEST;
  }
}
