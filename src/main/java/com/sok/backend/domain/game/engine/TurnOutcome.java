package com.sok.backend.domain.game.engine;

import java.util.List;

/**
 * The resolution of a single {@link Turn}. Sealed so the engine can exhaustively dispatch.
 *
 * <p>Your three first-class situations are {@link Expanding}, {@link FailedAttack} and
 * {@link TieBreaking}. {@link Placed}, {@link Ranked} and {@link NoOp} exist to keep phases other
 * than attack (placement, claiming, waiting) honest instead of forcing them into the attack
 * outcome vocabulary.
 */
public sealed interface TurnOutcome
    permits TurnOutcome.Expanding,
        TurnOutcome.FailedAttack,
        TurnOutcome.TieBreaking,
        TurnOutcome.Placed,
        TurnOutcome.Ranked,
        TurnOutcome.NoOp {

  /** Attacker took the region (or a player placed a castle on unclaimed territory). */
  record Expanding(String actorUid, int regionId, int hpDelta) implements TurnOutcome {}

  /** Attacker failed to take the region; defender stands. */
  record FailedAttack(String attackerUid, String defenderUid) implements TurnOutcome {}

  /** Turn resolved to a tie that needs a tie-breaker sub-flow to decide a real winner. */
  record TieBreaking(String reason, String strategyId) implements TurnOutcome {}

  /** A non-contested placement (initial castle, free move). */
  record Placed(String actorUid, int regionId) implements TurnOutcome {}

  /** Multi-actor turn produced an ordering (claiming-question ranks). */
  record Ranked(List<String> orderedUids) implements TurnOutcome {}

  /** Turn consumed the event without changing phase state (validation, partial input). */
  record NoOp() implements TurnOutcome {
    public static final NoOp INSTANCE = new NoOp();
  }
}
