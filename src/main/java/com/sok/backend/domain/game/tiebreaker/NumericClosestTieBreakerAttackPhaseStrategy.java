package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.service.config.GameRuntimeConfig;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Estimation tie-break (closest guess). Opens {@code battle_tiebreaker_start} and schedules the numeric
 * deadline callback supplied by the gateway.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class NumericClosestTieBreakerAttackPhaseStrategy implements TieBreakerAttackPhaseStrategy {

  @Override
  public boolean supports(String normalizedEffectiveMode, String defenderUid) {
    return TieBreakerModeIds.NUMERIC_CLOSEST.equals(normalizedEffectiveMode);
  }

  @Override
  public void begin(BeginContext ctx) {
    DuelState duel = ctx.duel();
    TieBreakerRealtimeBridge b = ctx.bridge();
    duel.tiebreakKind = "numeric";
    duel.numericQuestion = b.questionEngine().nextNumericQuestion();
    GameRuntimeConfig cfg = b.configuration();
    Map<String, Object> payload =
        b.questionEngine()
            .toClient(duel.numericQuestion, ctx.phaseStartedAtMs(), cfg.getTiebreakDurationMs());
    b.emitToRoom("battle_tiebreaker_start", payload);
    b.scheduleRoomTimer(
        "tiebreak_timeout",
        cfg.getTiebreakDurationMs() + 50L,
        ctx.onNumericTiebreakDeadline());
  }
}
