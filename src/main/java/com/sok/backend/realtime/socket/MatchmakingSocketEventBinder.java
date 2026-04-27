package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.domain.game.GameInputRules;
import com.sok.backend.realtime.binding.SocketEventBinder;
import com.sok.backend.realtime.match.CastlePlacementOrchestrator;
import com.sok.backend.realtime.matchmaking.MatchmakingAllocator;
import com.sok.backend.realtime.room.RoomSerialCommandService;
import com.sok.backend.realtime.room.RoomStore;
import com.sok.backend.realtime.room.RoomRulesResolver;
import com.sok.backend.realtime.room.RoomLifecycle;
import com.sok.backend.realtime.socket.match.MatchJoinRollbackService;
import com.sok.backend.realtime.socket.match.MatchRejoinService;
import com.sok.backend.realtime.socket.match.NewPlayerRoomJoinService;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import org.springframework.stereotype.Component;

import static com.sok.backend.realtime.room.RoomClientSnapshotFactory.mapOf;

/**
 * Inbound: {@code join_matchmaking}, {@code leave_matchmaking}, {@code start_match} (moved from
 * {@code SocketGateway}).
 */
@Component
public class MatchmakingSocketEventBinder implements SocketEventBinder {

  private final GameInputRules gameInputRules;
  private final NewPlayerRoomJoinService newPlayerRoomJoin;
  private final MatchRejoinService matchRejoinService;
  private final MatchJoinRollbackService rollback;
  private final RoomStore store;
  private final RoomSerialCommandService roomCommands;
  private final MatchmakingAllocator matchmakingAllocator;
  private final RoomLifecycle lifecycle;
  private final RuntimeGameConfigService runtimeConfigService;
  private final RoomRulesResolver rulesResolver;
  private final CastlePlacementOrchestrator castlePlacement;

  public MatchmakingSocketEventBinder(
      GameInputRules gameInputRules,
      NewPlayerRoomJoinService newPlayerRoomJoin,
      MatchRejoinService matchRejoinService,
      MatchJoinRollbackService rollback,
      RoomStore store,
      RoomSerialCommandService roomCommands,
      MatchmakingAllocator matchmakingAllocator,
      RoomLifecycle lifecycle,
      RuntimeGameConfigService runtimeConfigService,
      RoomRulesResolver rulesResolver,
      CastlePlacementOrchestrator castlePlacement) {
    this.gameInputRules = gameInputRules;
    this.newPlayerRoomJoin = newPlayerRoomJoin;
    this.matchRejoinService = matchRejoinService;
    this.rollback = rollback;
    this.store = store;
    this.roomCommands = roomCommands;
    this.matchmakingAllocator = matchmakingAllocator;
    this.lifecycle = lifecycle;
    this.runtimeConfigService = runtimeConfigService;
    this.rulesResolver = rulesResolver;
    this.castlePlacement = castlePlacement;
  }

  @Override
  public void bind(SocketIOServer server) {
    server.addEventListener(
        "join_matchmaking",
        JsonNode.class,
        (client, payload, ackRequest) -> onJoinMatchmaking(client, payload, server));

    server.addEventListener(
        "leave_matchmaking",
        JsonNode.class,
        (client, payload, ack) -> onLeaveMatchmaking(client, payload, server));

    server.addEventListener(
        "start_match",
        JsonNode.class,
        (client, payload, ack) -> onStartMatch(client, payload, server));
  }

  private void onJoinMatchmaking(
      SocketIOClient client, JsonNode payload, SocketIOServer server) {
    if (!SocketEventPayloads.payloadHasSocketUid(client, payload, "uid")) {
      client.disconnect();
      return;
    }
    String uid = SocketEventPayloads.asString(payload, "uid");
    String name = SocketEventPayloads.asString(payload, "name");
    if (name.trim().isEmpty()) {
      name = "Warrior";
    }
    final String finalName = name;
    String avatarRaw = SocketEventPayloads.asString(payload, "avatarURL");
    if (avatarRaw.trim().isEmpty()) {
      avatarRaw = SocketEventPayloads.asString(payload, "avatarUrl");
    }
    final String joinAvatar =
        avatarRaw.trim().isEmpty() ? null : avatarRaw.trim();
    final String joinMatchMode = payload.path("matchMode").asText("");
    final String joinRulesetId = payload.path("rulesetId").asText("");
    final String joinMapId = payload.path("mapId").asText("");
    String privateCode = payload.path("privateCode").asText("");
    String normalized = gameInputRules.normalizePrivateCode(privateCode);

    MatchRejoinService.RejoinResult rj =
        matchRejoinService.rejoinIfApplicable(client, uid, server, joinAvatar);
    if (rj == MatchRejoinService.RejoinResult.SERVER_BUSY) {
      client.sendEvent("join_rejected", mapOf("reason", "server_busy"));
      return;
    }
    if (rj == MatchRejoinService.RejoinResult.REJOIN_QUEUED) {
      return;
    }

    if (newPlayerRoomJoin.isServerFull()) {
      client.sendEvent("join_rejected", mapOf("reason", "server_full"));
      return;
    }
    MatchmakingAllocator.Allocation allocation =
        matchmakingAllocator.findOrCreateRoomAllocation(normalized);
    if (allocation == null) {
      client.sendEvent("join_rejected", mapOf("reason", "capacity"));
      return;
    }
    final String assignedRoomId = allocation.roomId();
    if (!roomCommands.runWithServer(
        assignedRoomId,
        server,
        (s, room) ->
            newPlayerRoomJoin.runJoinIntoRoom(
                client,
                s,
                room,
                uid,
                finalName,
                joinAvatar,
                joinMatchMode,
                joinRulesetId,
                joinMapId))) {
      rollback.rollbackOrphanWaitingRoom(assignedRoomId, allocation.brandNewEmpty());
      client.sendEvent("join_rejected", mapOf("reason", "server_busy"));
    }
  }

  private void onLeaveMatchmaking(SocketIOClient client, JsonNode payload, SocketIOServer server) {
    SocketEventPayloads.validateUidOrDisconnect(client, payload);
    String uid = SocketEventPayloads.asString(payload, "uid");
    if (uid.trim().isEmpty()) {
      return;
    }
    String roomId = store.roomIdForUid(uid);
    if (roomId == null) {
      return;
    }
    roomCommands.runInRoom(roomId, room -> lifecycle.removePlayerFromRoom(room, uid));
  }

  private void onStartMatch(SocketIOClient client, JsonNode payload, SocketIOServer server) {
    SocketEventPayloads.validateUidOrDisconnect(client, payload);
    String roomId = SocketEventPayloads.asString(payload, "roomId");
    String uid = SocketEventPayloads.asString(payload, "uid");
    roomCommands.runWithServer(
        roomId,
        server,
        (s, room) -> {
          if (room.inviteCode == null) {
            return;
          }
          if (!GamePhases.WAITING.equals(room.phase)) {
            return;
          }
          if (!uid.equals(room.hostUid)) {
            return;
          }
          GameRuntimeConfig cfg = runtimeConfigService.get();
          if (room.players.size() < rulesResolver.requiredPlayersToStart(room, cfg)
              || room.players.size() > cfg.getMaxPlayers()) {
            return;
          }
          castlePlacement.startCastlePlacementPhase(s, room);
        });
  }
}
