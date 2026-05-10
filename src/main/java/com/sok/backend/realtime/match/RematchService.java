package com.sok.backend.realtime.match;

import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomStateFactory;
import com.sok.backend.realtime.room.RoomTimerScheduler;
import com.sok.backend.service.config.RuntimeGameConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Domain service for managing rematch voting and triggering new games.
 * Decouples voting logic from socket event handlers.
 */
@Component
public class RematchService {
  private static final Logger log = LoggerFactory.getLogger(RematchService.class);
  private static final String PHASE_ENDED = "ended";

  private final RoomBroadcaster broadcaster;
  private final CastlePlacementOrchestrator castlePlacement;
  private final RoomStateFactory roomStateFactory;
  private final RuntimeGameConfigService runtimeConfigService;
  private final RoomTimerScheduler roomTimers;

  public RematchService(
      RoomBroadcaster broadcaster,
      CastlePlacementOrchestrator castlePlacement,
      RoomStateFactory roomStateFactory,
      RuntimeGameConfigService runtimeConfigService,
      RoomTimerScheduler roomTimers) {
    this.broadcaster = broadcaster;
    this.castlePlacement = castlePlacement;
    this.roomStateFactory = roomStateFactory;
    this.runtimeConfigService = runtimeConfigService;
    this.roomTimers = roomTimers;
  }

  /**
   * Records a vote for a rematch and triggers a new game if all players agree.
   * If uid is null, it just evaluates the current state (used when a player leaves).
   */
  public void processRematchVote(RoomState room, String uid) {
    if (!PHASE_ENDED.equals(room.phase)) {
      return;
    }

    if (uid != null) {
      if (!room.playersByUid.containsKey(uid)) {
        log.warn("sok rematch vote rejected: player {} not in room {}", uid, room.id);
        return;
      }
      room.rematchVotes.put(uid, true);
      log.info("sok player {} voted for rematch in room {}", uid, room.id);
    }

    if (shouldTriggerRematch(room)) {
      triggerRematch(room);
    } else if (uid != null) {
      broadcaster.emitRoomUpdate(room);
    }
  }

  private boolean shouldTriggerRematch(RoomState room) {
    if (room.players.isEmpty()) return false;
    
    int votes = 0;
    for (PlayerState p : room.players) {
      if (room.rematchVotes.getOrDefault(p.uid, false)) {
        votes++;
      }
    }
    return votes >= room.players.size();
  }

  private void triggerRematch(RoomState room) {
    log.info("sok all players voted for rematch in room {}. Starting new game.", room.id);
    roomTimers.cancelAll(room);
    room.rematchVotes.clear();
    room.regions = roomStateFactory.buildRegionsFromConfig(runtimeConfigService.get());
    castlePlacement.startCastlePlacementPhase(room);
  }
}
