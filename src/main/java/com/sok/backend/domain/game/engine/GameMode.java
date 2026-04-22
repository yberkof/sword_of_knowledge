package com.sok.backend.domain.game.engine;

import java.util.List;

/**
 * A pluggable description of one ruleset.
 *
 * <p>Implementations are Spring beans picked up by {@link GameModeRegistry}, keyed by {@link #id()}
 * which maps one-to-one with {@code rulesetId} on the wire. Adding a new game mode is as small as
 * declaring a new {@code @Component} that implements this interface; the gateway never changes.
 *
 * <p>A mode contributes three things:
 * <ul>
 *   <li>{@link #rules()} — numeric / policy overrides (friendly-fire, timers, pick counts, …);</li>
 *   <li>{@link #phaseOrder()} — the canonical sequence of phase ids the match walks through;</li>
 *   <li>optional {@link Phase} beans when the mode changes *how* a phase resolves, not just its
 *       numbers. Phase beans advertise which {@link PhaseId} they support via {@code id()} and are
 *       selected per-mode by {@link #phaseFor(PhaseId)}.</li>
 * </ul>
 */
public interface GameMode {

  /** Matches {@code rulesetId} echoed on every {@code room_update}. */
  String id();

  /** Human-readable label for logs / admin dashboards. */
  default String displayName() {
    return id();
  }

  /** Per-mode rule overrides. Must never be {@code null}. */
  ModeRules rules();

  /** Canonical phase sequence. {@link PhaseId#WAITING} and {@link PhaseId#ENDED} are terminal. */
  default List<PhaseId> phaseOrder() {
    return List.of(
        PhaseId.WAITING,
        PhaseId.CASTLE_PLACEMENT,
        PhaseId.CLAIMING_QUESTION,
        PhaseId.CLAIMING_PICK,
        PhaseId.BATTLE,
        PhaseId.ENDED);
  }

  /**
   * Resolve the {@link Phase} implementation this mode uses for {@code phaseId}, or {@code null}
   * when the mode wants the engine to fall back to its built-in phase (currently the legacy
   * {@code SocketGateway} pipeline).
   */
  default Phase phaseFor(PhaseId phaseId) {
    return null;
  }
}
