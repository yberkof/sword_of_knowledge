package com.sok.backend.realtime.match;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.QuestionEngineService;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomClientSnapshotFactory;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Handles all socket emissions for the battle phase.
 * Decouples domain logic from the transport layer.
 */
@Component
public class BattleNotificationService {

  private final RoomBroadcaster broadcaster;
  private final RoomClientSnapshotFactory snapshotFactory;
  private final QuestionEngineService questionEngineService;

  public BattleNotificationService(
      RoomBroadcaster broadcaster,
      RoomClientSnapshotFactory snapshotFactory,
      QuestionEngineService questionEngineService) {
    this.broadcaster = broadcaster;
    this.snapshotFactory = snapshotFactory;
    this.questionEngineService = questionEngineService;
  }

  public void notifyPhaseChanged(SocketIOServer server, RoomState room, String phase) {
    HashMap<String, Object> payload = new HashMap<>();
    payload.put("phase", phase);
    payload.put("round", room.round);
    server.getRoomOperations(room.id).sendEvent("phase_changed", payload);
    broadcaster.emitRoomUpdate(room);
  }

  public void notifyDuelStart(SocketIOServer server, RoomState room, DuelState duel, long now, int durationMs) {
    HashMap<String, Object> payload = new HashMap<>();
    payload.put("question", questionEngineService.toClient(duel.mcqQuestion, now, durationMs));
    payload.put("serverNowMs", now);
    payload.put("phaseEndsAt", now + durationMs);
    payload.put("duelDurationMs", durationMs);
    payload.put("hiddenOptionIndices", new java.util.ArrayList<Integer>());
    payload.put("duelHammerConsumed", false);
    payload.put("attackerUid", duel.attackerUid);
    payload.put("defenderUid", duel.defenderUid);
    payload.put("targetHexId", duel.targetRegionId);

    server.getRoomOperations(room.id).sendEvent("duel_start", payload);
    broadcaster.emitRoomUpdate(room);
  }

  public void notifyDuelResolved(
      SocketIOServer server, 
      RoomState room, 
      Map<String, Object> result) {
    HashMap<String, Object> payload = new HashMap<>();
    payload.put("room", snapshotFactory.roomToClient(room));
    payload.put("result", result);
    server.getRoomOperations(room.id).sendEvent("duel_resolved", payload);
    broadcaster.emitRoomUpdate(room);
  }
}
