package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Avoid-bombs tie-break minigame. Opens the placement sub-phase, schedules the placement deadline,
 * and emits the public start payload. Event-level handling of {@code tiebreaker_avoid_bombs_place}
 * and {@code tiebreaker_avoid_bombs_open} lives in {@code SocketGateway}; this strategy is only
 * responsible for bootstrapping the duel into the tie-break state.
 *
 * <p>Ordered above {@link NumericClosestTieBreakerAttackPhaseStrategy} (lowest precedence) so the
 * composer picks this up whenever the effective mode id resolves to
 * {@link TieBreakerModeIds#MINIGAME_AVOID_BOMBS}.
 */
@Component
@Order(90)
public class AvoidBombsTieBreakerAttackPhaseStrategy implements TieBreakerAttackPhaseStrategy {

  /** Timer key used for the placement-window deadline — cancelled early if both players lock in. */
  public static final String PLACEMENT_TIMER_KEY = "avoid_bombs_placement";

  private final AvoidBombsMinigameStarter avoidBombsMinigameStarter;

  public AvoidBombsTieBreakerAttackPhaseStrategy(AvoidBombsMinigameStarter avoidBombsMinigameStarter) {
    this.avoidBombsMinigameStarter = avoidBombsMinigameStarter;
  }

  @Override
  public boolean supports(String normalizedEffectiveMode, String defenderUid) {
    return TieBreakerModeIds.MINIGAME_AVOID_BOMBS.equals(normalizedEffectiveMode)
        && !"neutral".equals(defenderUid);
  }

  @Override
  public void begin(BeginContext ctx) {
    TieBreakerRealtimeBridge bridge = ctx.bridge();
    avoidBombsMinigameStarter.start(ctx.duel(), bridge);
  }
}
