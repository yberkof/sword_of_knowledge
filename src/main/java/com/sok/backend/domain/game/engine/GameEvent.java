package com.sok.backend.domain.game.engine;

/**
 * Normalised representation of every inbound socket event that can drive a match.
 *
 * <p>The gateway (transport layer) translates raw {@code JsonNode} payloads into one of these
 * variants and hands them to {@code MatchEngine}. Keeping this as a sealed interface means each
 * {@link Phase} can pattern-match on the events it cares about and ignore the rest.
 */
public sealed interface GameEvent
    permits GameEvent.PlaceCastle,
        GameEvent.ClaimRegion,
        GameEvent.Attack,
        GameEvent.SubmitAnswer,
        GameEvent.SubmitEstimation,
        GameEvent.XoMove,
        GameEvent.UsePowerup,
        GameEvent.TimerFired,
        GameEvent.PlayerDisconnected {

  /** Socket uid of the actor (or {@code "system"} for {@link TimerFired}). */
  String uid();

  record PlaceCastle(String uid, int regionId) implements GameEvent {}

  record ClaimRegion(String uid, int regionId) implements GameEvent {}

  record Attack(String uid, int targetRegionId) implements GameEvent {}

  record SubmitAnswer(String uid, int choice, long latencyMs) implements GameEvent {}

  record SubmitEstimation(String uid, int value, long latencyMs) implements GameEvent {}

  record XoMove(String uid, int cellIndex) implements GameEvent {}

  record UsePowerup(String uid, String powerupId, Integer regionId) implements GameEvent {}

  record TimerFired(String timerId) implements GameEvent {
    @Override
    public String uid() {
      return "system";
    }
  }

  record PlayerDisconnected(String uid) implements GameEvent {}
}
