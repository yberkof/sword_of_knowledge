package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.tiebreaker.CollectionTieBreakService;
import com.sok.backend.domain.game.tiebreaker.MemoryTieBreakInteractionService;
import com.sok.backend.domain.game.tiebreaker.RhythmTieBreakInteractionService;
import com.sok.backend.domain.game.tiebreaker.RpsTieBreakInteractionService;
import com.sok.backend.domain.game.tiebreaker.TieBreakerRealtimeBridge;
import com.sok.backend.realtime.RoundLastSubmitEmitter;
import com.sok.backend.realtime.TieBreakMinigameScheduler;
import com.sok.backend.realtime.match.BattleOrchestrator;
import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.persistence.RoomSnapshotCoordinator;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomTimerScheduler;
import org.springframework.stereotype.Component;

/** Collection, RPS, rhythm, and memory tie-breaker commands. */
@Component
public class TiebreakRestEventHandler {

  private final CollectionTieBreakService collectionTieBreakService;
  private final RpsTieBreakInteractionService rpsTieBreakInteractionService;
  private final RhythmTieBreakInteractionService rhythmTieBreakInteractionService;
  private final MemoryTieBreakInteractionService memoryTieBreakInteractionService;
  private final TieBreakMinigameScheduler tieBreakMinigameScheduler;
  private final BattleOrchestrator battle;
  private final RoomBroadcaster broadcaster;
  private final RoomSnapshotCoordinator snapshotCoordinator;
  private final RoomTimerScheduler roomTimers;
  private final RoundLastSubmitEmitter roundLastSubmitEmitter;

  public TiebreakRestEventHandler(
      CollectionTieBreakService collectionTieBreakService,
      RpsTieBreakInteractionService rpsTieBreakInteractionService,
      RhythmTieBreakInteractionService rhythmTieBreakInteractionService,
      MemoryTieBreakInteractionService memoryTieBreakInteractionService,
      TieBreakMinigameScheduler tieBreakMinigameScheduler,
      BattleOrchestrator battle,
      RoomBroadcaster broadcaster,
      RoomSnapshotCoordinator snapshotCoordinator,
      RoomTimerScheduler roomTimers,
      RoundLastSubmitEmitter roundLastSubmitEmitter) {
    this.collectionTieBreakService = collectionTieBreakService;
    this.rpsTieBreakInteractionService = rpsTieBreakInteractionService;
    this.rhythmTieBreakInteractionService = rhythmTieBreakInteractionService;
    this.memoryTieBreakInteractionService = memoryTieBreakInteractionService;
    this.tieBreakMinigameScheduler = tieBreakMinigameScheduler;
    this.battle = battle;
    this.broadcaster = broadcaster;
    this.snapshotCoordinator = snapshotCoordinator;
    this.roomTimers = roomTimers;
    this.roundLastSubmitEmitter = roundLastSubmitEmitter;
  }

  public void onCollectionPick(
      SocketIOServer server, String roomId, String uid, String choice, RoomState room) {
    if (room == null || !GamePhases.BATTLE_TIEBREAKER.equals(room.phase) || room.activeDuel == null) {
      return;
    }
    DuelState duel = room.activeDuel;
    TieBreakerRealtimeBridge bridge = battle.tieBreakerBridge(server, room);
    collectionTieBreakService.submitPick(duel, uid, choice, bridge);
    roundLastSubmitEmitter.emit(
        server, room, "tiebreak_collection", null, false, "collection_pick", uid);
    broadcaster.emitRoomUpdate(room);
    snapshotCoordinator.snapshotDurable(room);
  }

  public void onRpsThrow(
      SocketIOServer server, String roomId, String uid, String hand, RoomState room) {
    if (room == null || !GamePhases.BATTLE_TIEBREAKER.equals(room.phase) || room.activeDuel == null) {
      return;
    }
    DuelState duel = room.activeDuel;
    RpsTieBreakInteractionService.MoveOutcome mo = rpsTieBreakInteractionService.submitThrow(duel, uid, hand);
    tieBreakMinigameScheduler.applyRpsOutcome(server, room, mo);
  }

  public void onRhythmSubmit(
      SocketIOServer server, String roomId, String uid, int[] inputs, RoomState room) {
    if (room == null || !GamePhases.BATTLE_TIEBREAKER.equals(room.phase) || room.activeDuel == null) {
      return;
    }
    DuelState duel = room.activeDuel;
    TieBreakerRealtimeBridge bridge = battle.tieBreakerBridge(server, room);
    RhythmTieBreakInteractionService.MoveOutcome mo =
        rhythmTieBreakInteractionService.submitReplay(duel, uid, inputs, bridge);
    roomTimers.cancelTimer(room, TieBreakMinigameScheduler.RHYTHM_ROUND_TIMER_KEY);
    tieBreakMinigameScheduler.applyRhythmOutcome(server, room, mo);
  }

  public void onMemoryFlip(
      com.corundumstudio.socketio.SocketIOClient client,
      SocketIOServer server,
      String roomId,
      String uid,
      int cellIndex,
      RoomState room) {
    if (room == null || !GamePhases.BATTLE_TIEBREAKER.equals(room.phase) || room.activeDuel == null) {
      return;
    }
    DuelState duel = room.activeDuel;
    MemoryTieBreakInteractionService.FlipOutcome fo = memoryTieBreakInteractionService.flip(duel, uid, cellIndex);
    switch (fo.type()) {
      case INVALID_PHASE:
      case INVALID_PARTICIPANT:
      case INVALID_CELL:
      case NOT_YOUR_TURN:
      case CELL_ALREADY_MATCHED:
        client.sendEvent(
            "tiebreaker_memory_invalid", com.sok.backend.realtime.room.RoomClientSnapshotFactory.mapOf("reason", fo.type().name()));
        return;
      default:
        tieBreakMinigameScheduler.applyMemoryFlipOutcome(server, room, fo, true, uid);
        break;
    }
  }
}
