package com.sok.backend.realtime;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.tiebreaker.MemoryTieBreakInteractionService;
import com.sok.backend.domain.game.tiebreaker.RhythmTieBreakInteractionService;
import com.sok.backend.domain.game.tiebreaker.RpsTieBreakInteractionService;
import com.sok.backend.domain.game.tiebreaker.TieBreakerRealtimeBridge;
import com.sok.backend.realtime.match.BattleOrchestrator;
import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.persistence.RoomSnapshotCoordinator;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomExecutorRegistry;
import com.sok.backend.realtime.room.RoomStore;
import com.sok.backend.realtime.room.RoomTimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Schedules rhythm/memory deadlines for tie-break minigames so timer callbacks have access to
 * {@link SocketIOServer} and {@link BattleOrchestrator#finishBattle}.
 */
@Component
public class TieBreakMinigameScheduler {

  private static final Logger log = LoggerFactory.getLogger(TieBreakMinigameScheduler.class);

  public static final String RHYTHM_ROUND_TIMER_KEY = "rhythm_round";
  public static final String MEMORY_PEEK_TIMER_KEY = "memory_peek";

  private final SocketIOServer socketServer;
  private final RoomStore store;
  private final RoomExecutorRegistry executors;
  private final RoomTimerScheduler roomTimers;
  private final BattleOrchestrator battleOrchestrator;
  private final RhythmTieBreakInteractionService rhythmService;
  private final MemoryTieBreakInteractionService memoryService;
  private final RoomBroadcaster broadcaster;
  private final RoomSnapshotCoordinator snapshotCoordinator;

  public TieBreakMinigameScheduler(
      SocketIOServer socketServer,
      RoomStore store,
      RoomExecutorRegistry executors,
      RoomTimerScheduler roomTimers,
      BattleOrchestrator battleOrchestrator,
      RhythmTieBreakInteractionService rhythmService,
      MemoryTieBreakInteractionService memoryService,
      RoomBroadcaster broadcaster,
      RoomSnapshotCoordinator snapshotCoordinator) {
    this.socketServer = socketServer;
    this.store = store;
    this.executors = executors;
    this.roomTimers = roomTimers;
    this.battleOrchestrator = battleOrchestrator;
    this.rhythmService = rhythmService;
    this.memoryService = memoryService;
    this.broadcaster = broadcaster;
    this.snapshotCoordinator = snapshotCoordinator;
  }

  public void scheduleRhythmRoundDeadline(String roomId) {
    RoomState room = store.get(roomId);
    if (room == null || room.activeDuel == null) return;
    DuelState duel = room.activeDuel;
    if (!"rhythm".equals(duel.tiebreakKind) || duel.rhythmRoundDeadlineAtMs == null) return;
    long delay = Math.max(50L, duel.rhythmRoundDeadlineAtMs - System.currentTimeMillis() + 80);
    roomTimers.cancelTimer(room, RHYTHM_ROUND_TIMER_KEY);
    roomTimers.scheduleTimer(
        room,
        RHYTHM_ROUND_TIMER_KEY,
        delay,
        () ->
            executors.submitToRoom(
                roomId,
                () -> {
                  try {
                    onRhythmDeadline(roomId);
                  } catch (RuntimeException ex) {
                    log.error("sok rhythm deadline room={}", roomId, ex);
                  }
                }));
  }

  public void scheduleMemoryPeekEnd(String roomId) {
    RoomState room = store.get(roomId);
    if (room == null || room.activeDuel == null) return;
    DuelState duel = room.activeDuel;
    if (!"memory".equals(duel.tiebreakKind) || duel.memoryPeekEndsAtMs == null) return;
    long delay = Math.max(50L, duel.memoryPeekEndsAtMs - System.currentTimeMillis() + 80);
    roomTimers.cancelTimer(room, MEMORY_PEEK_TIMER_KEY);
    roomTimers.scheduleTimer(
        room,
        MEMORY_PEEK_TIMER_KEY,
        delay,
        () ->
            executors.submitToRoom(
                roomId,
                () -> {
                  try {
                    onMemoryPeekEnd(roomId);
                  } catch (RuntimeException ex) {
                    log.error("sok memory peek room={}", roomId, ex);
                  }
                }));
  }

  private void onRhythmDeadline(String roomId) {
    RoomState room = store.get(roomId);
    if (room == null || room.activeDuel == null) return;
    DuelState duel = room.activeDuel;
    if (!"rhythm".equals(duel.tiebreakKind)) return;
    TieBreakerRealtimeBridge bridge = battleOrchestrator.tieBreakerBridge(socketServer, room);
    RhythmTieBreakInteractionService.MoveOutcome mo =
        rhythmService.forceEvaluateIfReady(duel, bridge);
    applyRhythmOutcome(socketServer, room, mo);
  }

  private void onMemoryPeekEnd(String roomId) {
    RoomState room = store.get(roomId);
    if (room == null || room.activeDuel == null) return;
    DuelState duel = room.activeDuel;
    if (!"memory".equals(duel.tiebreakKind)) return;
    TieBreakerRealtimeBridge bridge = battleOrchestrator.tieBreakerBridge(socketServer, room);
    memoryService.endPeekStartPlay(duel, bridge);
    broadcaster.emitRoomUpdate(room);
    snapshotCoordinator.snapshotDurable(room);
  }

  public void applyRhythmOutcome(
      SocketIOServer server, RoomState room, RhythmTieBreakInteractionService.MoveOutcome mo) {
    DuelState duel = room.activeDuel;
    if (duel == null) return;
    switch (mo.type()) {
      case CONTINUE_NEXT_ROUND:
        broadcaster.emitRoomUpdate(room);
        snapshotCoordinator.snapshotDurable(room);
        scheduleRhythmRoundDeadline(room.id);
        break;
      case ATTACKER_WINS:
        battleOrchestrator.finishBattle(server, room, true, true, true, true, duel);
        break;
      case DEFENDER_WINS:
      case BOTH_FAIL_DEFENDER_WINS:
        battleOrchestrator.finishBattle(server, room, false, true, true, true, duel);
        break;
      default:
        broadcaster.emitRoomUpdate(room);
        snapshotCoordinator.snapshotDurable(room);
        break;
    }
  }

  public void applyRpsOutcome(
      SocketIOServer server, RoomState room, RpsTieBreakInteractionService.MoveOutcome mo) {
    DuelState duel = room.activeDuel;
    if (duel == null) return;
    switch (mo.type()) {
      case MATCH_ATTACKER_WINS:
        battleOrchestrator.finishBattle(server, room, true, true, true, true, duel);
        break;
      case MATCH_DEFENDER_WINS:
        battleOrchestrator.finishBattle(server, room, false, true, true, true, duel);
        break;
      case WAITING_PEER:
      case ROUND_ATTACKER_POINT:
      case ROUND_DEFENDER_POINT:
      case DRAW_ROUND:
        socketServer
            .getRoomOperations(room.id)
            .sendEvent(
                "tiebreaker_rps_round",
                com.sok.backend.domain.game.tiebreaker.CollectionTieBreakPayloadFactory.rpsRoundPayload(
                    room.id, duel, mo.type().name()));
        broadcaster.emitRoomUpdate(room);
        snapshotCoordinator.snapshotDurable(room);
        break;
      default:
        broadcaster.emitRoomUpdate(room);
        snapshotCoordinator.snapshotDurable(room);
        break;
    }
  }

  public void applyMemoryFlipOutcome(
      SocketIOServer server,
      RoomState room,
      MemoryTieBreakInteractionService.FlipOutcome fo,
      boolean emitFlipPayload) {
    DuelState duel = room.activeDuel;
    if (duel == null) return;
    switch (fo.type()) {
      case MATCH_ATTACKER_WINS:
        if (emitFlipPayload) {
          socketServer
              .getRoomOperations(room.id)
              .sendEvent(
                  "tiebreaker_memory_flip",
                  com.sok.backend.domain.game.tiebreaker.CollectionTieBreakPayloadFactory.memoryFlipPayload(
                      room.id,
                      duel,
                      fo.type().name(),
                      fo.firstIndex(),
                      fo.secondIndex(),
                      fo.revealedPairIds()));
        }
        battleOrchestrator.finishBattle(server, room, true, true, true, true, duel);
        break;
      case MATCH_DEFENDER_WINS:
      case MATCH_TIE_DEFENDER_WINS:
        if (emitFlipPayload) {
          socketServer
              .getRoomOperations(room.id)
              .sendEvent(
                  "tiebreaker_memory_flip",
                  com.sok.backend.domain.game.tiebreaker.CollectionTieBreakPayloadFactory.memoryFlipPayload(
                      room.id,
                      duel,
                      fo.type().name(),
                      fo.firstIndex(),
                      fo.secondIndex(),
                      fo.revealedPairIds()));
        }
        battleOrchestrator.finishBattle(server, room, false, true, true, true, duel);
        break;
      default:
        if (emitFlipPayload) {
          socketServer
              .getRoomOperations(room.id)
              .sendEvent(
                  "tiebreaker_memory_flip",
                  com.sok.backend.domain.game.tiebreaker.CollectionTieBreakPayloadFactory.memoryFlipPayload(
                      room.id,
                      duel,
                      fo.type().name(),
                      fo.firstIndex(),
                      fo.secondIndex(),
                      fo.revealedPairIds()));
        }
        broadcaster.emitRoomUpdate(room);
        snapshotCoordinator.snapshotDurable(room);
        break;
    }
  }
}
