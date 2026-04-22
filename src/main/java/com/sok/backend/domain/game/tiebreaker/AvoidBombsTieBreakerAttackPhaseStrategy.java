package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.service.config.GameRuntimeConfig;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log =
      LoggerFactory.getLogger(AvoidBombsTieBreakerAttackPhaseStrategy.class);

  /** Timer key used for the placement-window deadline — cancelled early if both players lock in. */
  public static final String PLACEMENT_TIMER_KEY = "avoid_bombs_placement";

  private final AvoidBombsTieBreakInteractionService interactionService;

  public AvoidBombsTieBreakerAttackPhaseStrategy(
      AvoidBombsTieBreakInteractionService interactionService) {
    this.interactionService = interactionService;
  }

  @Override
  public boolean supports(String normalizedEffectiveMode, String defenderUid) {
    return TieBreakerModeIds.MINIGAME_AVOID_BOMBS.equals(normalizedEffectiveMode)
        && !"neutral".equals(defenderUid);
  }

  @Override
  public void begin(BeginContext ctx) {
    DuelState duel = ctx.duel();
    TieBreakerRealtimeBridge bridge = ctx.bridge();
    GameRuntimeConfig cfg = bridge.configuration();
    long placementMs = Math.max(500L, cfg.getAvoidBombsPlacementMs());

    duel.tiebreakKind = "avoid_bombs";
    duel.avoidBombsSubPhase = "placement";
    duel.avoidBombsBoards.clear();
    duel.avoidBombsOpened.clear();
    duel.avoidBombsPlaced.clear();
    duel.avoidBombsHitsBy.clear();
    duel.avoidBombsTurnUid = null;

    Map<String, Object> start =
        AvoidBombsTieBreakPayloadFactory.startPayload(bridge.roomId(), duel, placementMs);
    log.info(
        "sok avoidBombs begin room={} attacker={} defender={} placementMs={}",
        bridge.roomId(),
        duel.attackerUid,
        duel.defenderUid,
        placementMs);
    bridge.emitToRoom("tiebreaker_avoid_bombs_start", start);

    bridge.scheduleRoomTimer(
        PLACEMENT_TIMER_KEY,
        placementMs + 50L,
        () -> {
          try {
            log.info(
                "sok avoidBombs placement timeout fired room={} kind={} sub={} placedAttacker={} placedDefender={}",
                bridge.roomId(),
                duel.tiebreakKind,
                duel.avoidBombsSubPhase,
                duel.avoidBombsPlaced.get(duel.attackerUid),
                duel.avoidBombsPlaced.get(duel.defenderUid));
            if (!"avoid_bombs".equals(duel.tiebreakKind)
                || !"placement".equals(duel.avoidBombsSubPhase)) {
              return;
            }
            interactionService.autofillPlacementsAndStart(duel);
            bridge.emitToRoom(
                "tiebreaker_avoid_bombs_ready",
                AvoidBombsTieBreakPayloadFactory.readyPayload(bridge.roomId(), duel));
            log.info(
                "sok avoidBombs placement autofilled room={} turnUid={}",
                bridge.roomId(),
                duel.avoidBombsTurnUid);
          } catch (RuntimeException ex) {
            log.error(
                "sok avoidBombs placement timeout FAILED room={} err={}",
                bridge.roomId(),
                ex.toString(),
                ex);
          }
        });
  }
}
