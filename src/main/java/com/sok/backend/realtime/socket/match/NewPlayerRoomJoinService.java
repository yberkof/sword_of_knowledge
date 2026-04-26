package com.sok.backend.realtime.socket.match;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.config.RealtimeScaleProperties;
import com.sok.backend.realtime.match.CastlePlacementOrchestrator;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.matchmaking.MatchmakingAllocator;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomRulesResolver;
import com.sok.backend.realtime.room.RoomStateFactory;
import com.sok.backend.realtime.room.RoomStore;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import org.springframework.stereotype.Component;

/**
 * Adds a new player to an allocated public/private room and starts castle placement when the lobby
 * is full (extracted from {@code SocketGateway}).
 */
@Component
public class NewPlayerRoomJoinService {

  private static final String PHASE_WAITING = "waiting";
  public static final java.util.List<String> PLAYER_COLORS =
      java.util.Arrays.asList("green", "blue", "red", "yellow", "purple", "orange", "teal", "pink");

  private final RuntimeGameConfigService runtimeConfigService;
  private final RoomRulesResolver rulesResolver;
  private final MatchmakingAllocator matchmakingAllocator;
  private final CastlePlacementOrchestrator castlePlacement;
  private final RealtimeScaleProperties scale;
  private final OnlinePlayerCountService onlineCount;
  private final RoomStore store;
  private final RoomBroadcaster broadcaster;

  public NewPlayerRoomJoinService(
      RuntimeGameConfigService runtimeConfigService,
      RoomRulesResolver rulesResolver,
      MatchmakingAllocator matchmakingAllocator,
      CastlePlacementOrchestrator castlePlacement,
      RealtimeScaleProperties scale,
      OnlinePlayerCountService onlineCount,
      RoomStore store,
      RoomBroadcaster broadcaster) {
    this.runtimeConfigService = runtimeConfigService;
    this.rulesResolver = rulesResolver;
    this.matchmakingAllocator = matchmakingAllocator;
    this.castlePlacement = castlePlacement;
    this.scale = scale;
    this.onlineCount = onlineCount;
    this.store = store;
    this.broadcaster = broadcaster;
  }

  public int requiredPlayersToStart(RoomState room, GameRuntimeConfig cfg) {
    return rulesResolver.requiredPlayersToStart(room, cfg);
  }

  public boolean isServerFull() {
    return scale.getMaxOnlinePlayers() > 0
        && onlineCount.currentOnline() >= scale.getMaxOnlinePlayers();
  }

  public void runJoinIntoRoom(
      SocketIOClient client,
      SocketIOServer server,
      RoomState room,
      String uid,
      String finalName,
      String joinMatchMode,
      String joinRulesetId,
      String joinMapId) {
    GameRuntimeConfig cfg = runtimeConfigService.get();
    if (room.players.size() >= cfg.getMaxPlayers()) {
      client.sendEvent("join_rejected", com.sok.backend.realtime.room.RoomClientSnapshotFactory.mapOf("reason", "room_full"));
      return;
    }
    if (room.players.isEmpty()) {
      if (joinMatchMode != null && !joinMatchMode.isEmpty()) {
        room.matchMode =
            RoomStateFactory.normalizeMatchMode(joinMatchMode, cfg.getDefaultMatchMode());
      }
      if (joinRulesetId != null && !joinRulesetId.isEmpty()) {
        room.rulesetId = joinRulesetId;
      }
      if (joinMapId != null && !joinMapId.isEmpty()) {
        room.mapId = joinMapId;
      }
    }
    PlayerState player = new PlayerState();
    player.uid = uid;
    player.name = finalName;
    player.socketId = client.getSessionId().toString();
    player.online = true;
    player.lastSeenAt = System.currentTimeMillis();
    player.color = PLAYER_COLORS.get(room.players.size() % PLAYER_COLORS.size());
    player.castleHp = cfg.getInitialCastleHp();
    room.players.add(player);
    room.playersByUid.put(uid, player);
    store.mapUidToRoom(uid, room.id);
    room.hostUid = room.players.get(0).uid;
    client.joinRoom(room.id);
    room.lastActivityAt = System.currentTimeMillis();
    matchmakingAllocator.updateSoloPublicIndexAfterJoin(room);
    broadcaster.emitRoomUpdate(room);
    if (room.players.size() >= requiredPlayersToStart(room, cfg) && PHASE_WAITING.equals(room.phase)) {
      castlePlacement.startCastlePlacementPhase(server, room);
    }
  }
}
