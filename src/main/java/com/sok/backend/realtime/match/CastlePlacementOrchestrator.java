package com.sok.backend.realtime.match;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.CastlePlacementPhaseService;
import com.sok.backend.realtime.matchmaking.MatchmakingAllocator;
import com.sok.backend.realtime.persistence.RoomSnapshotCoordinator;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomRulesResolver;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Starts and tracks progress of the castle placement phase. */
@Component
public class CastlePlacementOrchestrator {
  private static final String PHASE_CASTLE = "castle_placement";

  private final RuntimeGameConfigService runtimeConfigService;
  private final CastlePlacementPhaseService castlePlacementPhaseService;
  private final RoomBroadcaster broadcaster;
  private final RoomRulesResolver rulesResolver;
  private final MatchmakingAllocator matchmakingAllocator;
  private final RoomSnapshotCoordinator snapshotCoordinator;

  public CastlePlacementOrchestrator(
      RuntimeGameConfigService runtimeConfigService,
      CastlePlacementPhaseService castlePlacementPhaseService,
      RoomBroadcaster broadcaster,
      RoomRulesResolver rulesResolver,
      MatchmakingAllocator matchmakingAllocator,
      RoomSnapshotCoordinator snapshotCoordinator) {
    this.runtimeConfigService = runtimeConfigService;
    this.castlePlacementPhaseService = castlePlacementPhaseService;
    this.broadcaster = broadcaster;
    this.rulesResolver = rulesResolver;
    this.matchmakingAllocator = matchmakingAllocator;
    this.snapshotCoordinator = snapshotCoordinator;
  }

  public void startCastlePlacementPhase(SocketIOServer server, RoomState room) {
    matchmakingAllocator.clearIndexesForRoom(room);
    GameRuntimeConfig cfg = runtimeConfigService.get();
    rulesResolver.configureTeamsForMatch(room);
    room.phase = PHASE_CASTLE;
    room.round = 1;
    room.currentTurnIndex = 0;
    room.matchStartedAt = System.currentTimeMillis();
    room.lastActivityAt = room.matchStartedAt;
    room.scoreByUid.clear();
    for (PlayerState p : room.players) {
      p.castleHp = cfg.getInitialCastleHp();
      p.castleRegionId = null;
      p.isEliminated = false;
      p.score = 0;
      room.scoreByUid.put(p.uid, 0);
    }
    HashMap<String, Object> payload = new HashMap<>();
    payload.put("phase", PHASE_CASTLE);
    payload.put("initialCastleHp", cfg.getInitialCastleHp());
    server.getRoomOperations(room.id).sendEvent("phase_changed", payload);
    broadcaster.emitRoomUpdate(room);
    snapshotCoordinator.snapshotDurable(room);
  }

  public boolean allPlayersPlacedCastle(RoomState room) {
    List<String> active = new ArrayList<>();
    Map<String, Integer> castles = new HashMap<>();
    for (PlayerState p : room.players) {
      if (p.isEliminated) continue;
      active.add(p.uid);
      castles.put(p.uid, p.castleRegionId);
    }
    return castlePlacementPhaseService.allCastlesPlaced(active, castles);
  }
}
