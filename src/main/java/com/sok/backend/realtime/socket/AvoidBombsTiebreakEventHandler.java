package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.tiebreaker.AvoidBombsTieBreakInteractionService;
import com.sok.backend.domain.game.tiebreaker.AvoidBombsTieBreakPayloadFactory;
import com.sok.backend.domain.game.tiebreaker.AvoidBombsTieBreakerAttackPhaseStrategy;
import com.sok.backend.realtime.RoundLastSubmitEmitter;
import com.sok.backend.realtime.match.BattleOrchestrator;
import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomTimerScheduler;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

import static com.sok.backend.realtime.room.RoomClientSnapshotFactory.mapOf;

/** Avoid-bombs place/open tie-break. */
@Component
public class AvoidBombsTiebreakEventHandler {

  private final AvoidBombsTieBreakInteractionService service;
  private final BattleOrchestrator battle;
  private final RoomBroadcaster broadcaster;
  private final RoomTimerScheduler roomTimers;
  private final RoundLastSubmitEmitter roundLastSubmitEmitter;

  public AvoidBombsTiebreakEventHandler(
      AvoidBombsTieBreakInteractionService service,
      BattleOrchestrator battle,
      RoomBroadcaster broadcaster,
      RoomTimerScheduler roomTimers,
      RoundLastSubmitEmitter roundLastSubmitEmitter) {
    this.service = service;
    this.battle = battle;
    this.broadcaster = broadcaster;
    this.roomTimers = roomTimers;
    this.roundLastSubmitEmitter = roundLastSubmitEmitter;
  }

  public void onPlaceBombs(
      SocketIOClient client, SocketIOServer server, String roomId, String uid, List<Integer> cells, RoomState room) {
    if (room == null || !GamePhases.BATTLE_TIEBREAKER.equals(room.phase) || room.activeDuel == null) {
      return;
    }
    DuelState duel = room.activeDuel;
    AvoidBombsTieBreakInteractionService.MoveOutcome out = service.placeBombs(duel, uid, cells);
    switch (out.outcomeType()) {
      case INVALID_PHASE:
        client.sendEvent("tiebreaker_avoid_bombs_invalid", mapOf("reason", "bad_phase"));
        return;
      case INVALID_PARTICIPANT:
        client.sendEvent("tiebreaker_avoid_bombs_invalid", mapOf("reason", "not_participant"));
        return;
      case INVALID_DUPLICATE_PLACEMENT:
        client.sendEvent("tiebreaker_avoid_bombs_invalid", mapOf("reason", "already_placed"));
        return;
      case INVALID_LAYOUT:
        client.sendEvent("tiebreaker_avoid_bombs_invalid", mapOf("reason", "bad_layout"));
        return;
      case PLACEMENT_ACCEPTED:
        client.sendEvent(
            "tiebreaker_avoid_bombs_placed",
            AvoidBombsTieBreakPayloadFactory.placementAckPayload(room.id, uid, duel.avoidBombsBoards.get(uid)));
        return;
      case PLACEMENT_BOTH_READY:
        client.sendEvent(
            "tiebreaker_avoid_bombs_placed",
            AvoidBombsTieBreakPayloadFactory.placementAckPayload(room.id, uid, duel.avoidBombsBoards.get(uid)));
        roomTimers.cancelTimer(room, AvoidBombsTieBreakerAttackPhaseStrategy.PLACEMENT_TIMER_KEY);
        server
            .getRoomOperations(room.id)
            .sendEvent("tiebreaker_avoid_bombs_ready", AvoidBombsTieBreakPayloadFactory.readyPayload(room.id, duel));
        roundLastSubmitEmitter.emit(
            server, room, "tiebreak_avoid_bombs", null, false, "avoid_bombs_placement_ready", uid);
        broadcaster.emitRoomUpdate(room);
        return;
      default:
        return;
    }
  }

  public void onOpen(
      SocketIOClient client, SocketIOServer server, String roomId, String uid, int cellIndex, RoomState room) {
    if (room == null || !GamePhases.BATTLE_TIEBREAKER.equals(room.phase) || room.activeDuel == null) {
      return;
    }
    DuelState duel = room.activeDuel;
    AvoidBombsTieBreakInteractionService.MoveOutcome out = service.openCell(duel, uid, cellIndex);
    switch (out.outcomeType()) {
      case INVALID_PHASE:
        client.sendEvent("tiebreaker_avoid_bombs_invalid", mapOf("reason", "bad_phase"));
        return;
      case INVALID_PARTICIPANT:
        client.sendEvent("tiebreaker_avoid_bombs_invalid", mapOf("reason", "not_participant"));
        return;
      case INVALID_NOT_YOUR_TURN:
        client.sendEvent("tiebreaker_avoid_bombs_invalid", mapOf("reason", "not_your_turn"));
        return;
      case INVALID_CELL:
        client.sendEvent("tiebreaker_avoid_bombs_invalid", mapOf("reason", "bad_cell"));
        return;
      case INVALID_ALREADY_OPENED:
        client.sendEvent("tiebreaker_avoid_bombs_invalid", mapOf("reason", "already_opened"));
        return;
      case REVEAL_SAFE:
      case REVEAL_BOMB:
        server
            .getRoomOperations(room.id)
            .sendEvent(
                "tiebreaker_avoid_bombs_reveal",
                AvoidBombsTieBreakPayloadFactory.revealPayload(
                    room.id,
                    duel,
                    out.openerUid(),
                    out.targetUid(),
                    out.cellIndex(),
                    out.isBomb(),
                    out.nextTurnUid()));
        roundLastSubmitEmitter.emit(
            server,
            room,
            "tiebreak_avoid_bombs",
            out.openerUid(),
            false,
            "avoid_bombs_reveal",
            out.openerUid());
        broadcaster.emitRoomUpdate(room);
        return;
      case ATTACKER_WIN:
      case DEFENDER_WIN:
        server
            .getRoomOperations(room.id)
            .sendEvent(
                "tiebreaker_avoid_bombs_reveal",
                AvoidBombsTieBreakPayloadFactory.revealPayload(
                    room.id,
                    duel,
                    out.openerUid(),
                    out.targetUid(),
                    out.cellIndex(),
                    out.isBomb(),
                    null));
        server
            .getRoomOperations(room.id)
            .sendEvent(
                "tiebreaker_avoid_bombs_reveal_all", AvoidBombsTieBreakPayloadFactory.revealAllPayload(room.id, duel));
        battle.finishBattle(
            server,
            room,
            out.outcomeType() == AvoidBombsTieBreakInteractionService.OutcomeType.ATTACKER_WIN,
            true,
            true,
            true,
            duel);
        return;
      default:
        return;
    }
  }
}
