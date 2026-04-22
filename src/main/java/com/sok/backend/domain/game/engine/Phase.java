package com.sok.backend.domain.game.engine;

/**
 * A single phase of a match. Implementations are stateless Spring beans; all per-room state is
 * threaded through the {@link MatchContext}. A phase is driven by {@link GameEvent}s and returns
 * {@link TurnOutcome}s that the engine uses to decide whether to stay in the phase, advance to
 * the next phase, or branch into a sub-flow (e.g. tie-break).
 */
public interface Phase {

  /** Which phase this bean implements. The engine routes {@link GameEvent}s only to the matching phase. */
  PhaseId id();

  /**
   * Called once when the engine enters this phase for a given match. Seed per-phase fields on
   * {@link MatchContext#room()} here (questions, timers, turn order). The phase should emit any
   * opening events (question card, phase banner) via {@link MatchContext#bridge()}.
   */
  default void enter(MatchContext ctx) {}

  /**
   * Apply a {@link GameEvent} to the match. Returning anything other than {@link TurnOutcome.NoOp}
   * tells the engine "something decisive happened"; returning {@code NoOp} means "event accepted
   * but no phase-level progress".
   */
  TurnOutcome handle(MatchContext ctx, GameEvent event);

  /** Cleanup hook invoked when the engine transitions away from this phase. Cancel timers here. */
  default void exit(MatchContext ctx) {}
}
