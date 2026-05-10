package com.sok.backend.realtime.match;

import com.sok.backend.realtime.matchmaking.MatchmakingAllocator;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomRulesResolver;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import java.util.HashMap;
import org.springframework.stereotype.Component;

/**
 * Handles the initialization and reset of room and player state for a new match.
 * Extracts this responsibility from phase orchestrators to follow SRP.
 */
@Component
public class MatchInitializer {

  private final RuntimeGameConfigService runtimeConfigService;
  private final RoomRulesResolver rulesResolver;
  private final MatchmakingAllocator matchmakingAllocator;
  private final RoomBroadcaster broadcaster;

  public MatchInitializer(
      RuntimeGameConfigService runtimeConfigService,
      RoomRulesResolver rulesResolver,
      MatchmakingAllocator matchmakingAllocator,
      RoomBroadcaster broadcaster) {
    this.runtimeConfigService = runtimeConfigService;
    this.rulesResolver = rulesResolver;
    this.matchmakingAllocator = matchmakingAllocator;
    this.broadcaster = broadcaster;
  }

  /**
   * Resets the room and player states to their initial values for a new match.
   */
  public void initializeMatchState(RoomState room) {
    matchmakingAllocator.clearIndexesForRoom(room);
    GameRuntimeConfig cfg = runtimeConfigService.get();
    rulesResolver.configureTeamsForMatch(room);

    room.round = 1;
    room.roundAttackCount = 0;
    room.currentTurnIndex = 0;
    room.matchStartedAt = System.currentTimeMillis();
    room.lastActivityAt = room.matchStartedAt;
    room.activeDuel = null;
    room.mcqSpeedTieRetries = 0;
    room.tieBreakOverride = null;
    room.claimQueue.clear();
    room.claimTurnUid = null;
    room.claimPicksLeftByUid.clear();
    room.scoreByUid.clear();

    for (PlayerState p : room.players) {
      p.castleHp = cfg.getInitialCastleHp();
      p.castleRegionId = null;
      p.isEliminated = false;
      p.eliminatedAt = null;
      p.score = 0;
      room.scoreByUid.put(p.uid, 0);
    }

    HashMap<String, Object> payload = new HashMap<>();
    payload.put("phase", room.phase);
    payload.put("initialCastleHp", cfg.getInitialCastleHp());
    broadcaster.sendToRoom(room.id, "phase_changed", payload);
    broadcaster.emitRoomUpdate(room);
  }
}
