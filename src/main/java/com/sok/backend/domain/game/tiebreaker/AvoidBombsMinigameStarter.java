package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.service.config.GameRuntimeConfig;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Boots the avoid-bombs minigame on an existing duel (used by {@link AvoidBombsTieBreakerAttackPhaseStrategy}
 * and collection vote resolution).
 */
@Component
public class AvoidBombsMinigameStarter {

  private static final Logger log = LoggerFactory.getLogger(AvoidBombsMinigameStarter.class);

  /** Same key as {@link AvoidBombsTieBreakerAttackPhaseStrategy#PLACEMENT_TIMER_KEY}. */
  static final String PLACEMENT_TIMER_KEY = "avoid_bombs_placement";

  private final AvoidBombsTieBreakInteractionService interactionService;

  public AvoidBombsMinigameStarter(AvoidBombsTieBreakInteractionService interactionService) {
    this.interactionService = interactionService;
  }

  /** Initializes avoid-bombs fields, emits {@code tiebreaker_avoid_bombs_start}, schedules placement timer. */
  public void start(DuelState duel, TieBreakerRealtimeBridge bridge) {
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
        "sok avoidBombs start room={} attacker={} defender={} placementMs={}",
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
