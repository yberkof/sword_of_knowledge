package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import java.util.HashMap;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Tic-tac-toe tie-break minigame. Higher precedence than {@link NumericClosestTieBreakerAttackPhaseStrategy}.
 */
@Component
@Order(100)
public class XoMinigameTieBreakerAttackPhaseStrategy implements TieBreakerAttackPhaseStrategy {

  @Override
  public boolean supports(String normalizedEffectiveMode, String defenderUid) {
    return TieBreakerModeIds.MINIGAME_XO.equals(normalizedEffectiveMode) && !"neutral".equals(defenderUid);
  }

  @Override
  public void begin(BeginContext ctx) {
    DuelState duel = ctx.duel();
    TieBreakerRealtimeBridge b = ctx.bridge();
    duel.tiebreakKind = "xo";
    duel.xoCells = XoBoardRules.emptyBoard();
    duel.xoTurnUid = duel.attackerUid;
    duel.xoReplayCount = 0;
    HashMap<String, Object> start = new HashMap<String, Object>();
    start.put("roomId", b.roomId());
    start.put("durationMs", b.configuration().getTiebreakDurationMs());
    start.put("xoPayload", XoTieBreakPayloadFactory.xoPayload(b.roomId(), duel));
    b.emitToRoom("tiebreaker_xo_start", start);
  }
}
