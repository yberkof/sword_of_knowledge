package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;

/**
 * Strategy for starting the {@code battle_tiebreaker} sub-phase after an MCQ speed tie (numeric
 * estimation, mini-games, …). Add new implementations as Spring beans; ordering via {@link
 * org.springframework.core.annotation.Order}.
 */
public interface TieBreakerAttackPhaseStrategy {

  boolean supports(String normalizedEffectiveMode, String defenderUid);

  void begin(BeginContext ctx);

  interface BeginContext {
    DuelState duel();

    TieBreakerRealtimeBridge bridge();

    long phaseStartedAtMs();

    /** Invoked when the numeric estimation timer fires (numeric strategy only). */
    Runnable onNumericTiebreakDeadline();
  }
}
