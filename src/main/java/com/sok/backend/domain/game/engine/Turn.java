package com.sok.backend.domain.game.engine;

/**
 * In-flight unit of resolution inside a {@link Phase}: a single attack, a single claim pick, one
 * multi-actor question round, etc. A {@code Turn} lives as long as it takes for the participating
 * players to produce enough input to yield a {@link TurnOutcome}.
 *
 * <p>For phases where turns are trivial (castle placement: each player's pick is its own one-shot
 * turn), implementations may skip creating a {@code Turn} and resolve directly inside
 * {@link Phase#handle(MatchContext, GameEvent)}.
 */
public interface Turn {

  /** Stable id (usually {@code attackerUid:regionId} or {@code claimPick:uid:n}) for logging/timers. */
  String id();

  /** {@code true} once the turn has collected every input it needs to produce an outcome. */
  boolean isReady();

  /**
   * Feed the turn one more event. Returning non-{@link TurnOutcome.NoOp} means the turn resolved
   * and the engine should transition accordingly.
   */
  TurnOutcome accept(MatchContext ctx, GameEvent event);
}
