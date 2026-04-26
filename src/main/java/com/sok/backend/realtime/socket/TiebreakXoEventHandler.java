package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.tiebreaker.XoTieBreakInteractionService;
import com.sok.backend.domain.game.tiebreaker.XoTieBreakPayloadFactory;
import com.sok.backend.realtime.RoundLastSubmitEmitter;
import com.sok.backend.realtime.match.BattleOrchestrator;
import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.sok.backend.realtime.room.RoomClientSnapshotFactory.mapOf;

/** {@code tiebreaker_xo_move} handler. */
@Component
public class TiebreakXoEventHandler {
  private static final Logger log = LoggerFactory.getLogger(TiebreakXoEventHandler.class);

  private final XoTieBreakInteractionService xoTieBreakInteractionService;
  private final RuntimeGameConfigService runtimeConfigService;
  private final BattleOrchestrator battle;
  private final RoomBroadcaster broadcaster;
  private final RoundLastSubmitEmitter roundLastSubmitEmitter;

  public TiebreakXoEventHandler(
      XoTieBreakInteractionService xoTieBreakInteractionService,
      RuntimeGameConfigService runtimeConfigService,
      BattleOrchestrator battle,
      RoomBroadcaster broadcaster,
      RoundLastSubmitEmitter roundLastSubmitEmitter) {
    this.xoTieBreakInteractionService = xoTieBreakInteractionService;
    this.runtimeConfigService = runtimeConfigService;
    this.battle = battle;
    this.broadcaster = broadcaster;
    this.roundLastSubmitEmitter = roundLastSubmitEmitter;
  }

  public void onMove(
      SocketIOClient client, SocketIOServer server, String roomId, String uid, int cellIndex, RoomState room) {
    if (room == null || !GamePhases.BATTLE_TIEBREAKER.equals(room.phase) || room.activeDuel == null) {
      return;
    }
    DuelState duel = room.activeDuel;
    if (!"xo".equals(duel.tiebreakKind) || duel.xoCells == null) {
      log.warn("sok tiebreaker_xo_move ignored: wrong tiebreak kind roomId={}", roomId);
      return;
    }
    if (!uid.equals(duel.xoTurnUid)) {
      log.warn(
          "sok tiebreaker_xo_move ignored: not your turn roomId={} uid={} turnUid={}",
          roomId,
          uid,
          duel.xoTurnUid);
      return;
    }
    if (!uid.equals(duel.attackerUid) && !uid.equals(duel.defenderUid)) {
      return;
    }
    if (cellIndex < 0 || cellIndex > 8) {
      log.warn("sok tiebreaker_xo_move ignored: bad cell roomId={} cellIndex={}", roomId, cellIndex);
      return;
    }
    GameRuntimeConfig cfg = runtimeConfigService.get();
    XoTieBreakInteractionService.MoveOutcome mo = xoTieBreakInteractionService.applyMove(duel, uid, cellIndex, cfg);
    switch (mo.outcomeType()) {
      case INVALID_OCCUPIED:
        client.sendEvent("tiebreaker_xo_invalid", mapOf("reason", "occupied"));
        return;
      case ATTACKER_WIN:
        battle.finishBattle(server, room, true, true, true, true, duel);
        return;
      case DEFENDER_WIN:
        battle.finishBattle(server, room, false, true, true, true, duel);
        return;
      case DRAW_REPLAY:
        server
            .getRoomOperations(room.id)
            .sendEvent(
                "tiebreaker_xo_replay", XoTieBreakPayloadFactory.replayPayload(room.id, mo.replayNumber()));
        roundLastSubmitEmitter.emit(
            server, room, "tiebreak_xo", null, true, "xo_replay", uid);
        broadcaster.emitRoomUpdate(room);
        return;
      case DRAW_DEFENDER_WINS:
        battle.finishBattle(server, room, false, true, true, true, duel);
        return;
      case CONTINUE:
        roundLastSubmitEmitter.emit(server, room, "tiebreak_xo", null, false, "xo_turn", uid);
        broadcaster.emitRoomUpdate(room);
        return;
      default:
        throw new IllegalStateException("Unhandled X-O outcome");
    }
  }
}
