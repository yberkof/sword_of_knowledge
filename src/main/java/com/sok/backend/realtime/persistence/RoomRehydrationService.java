package com.sok.backend.realtime.persistence;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.tiebreaker.AvoidBombsTieBreakInteractionService;
import com.sok.backend.domain.game.tiebreaker.AvoidBombsTieBreakPayloadFactory;
import com.sok.backend.domain.game.tiebreaker.AvoidBombsTieBreakerAttackPhaseStrategy;
import com.sok.backend.domain.game.tiebreaker.CollectionTieBreakService;
import com.sok.backend.domain.game.tiebreaker.TieBreakerRealtimeBridge;
import com.sok.backend.realtime.TieBreakMinigameScheduler;
import com.sok.backend.persistence.ActiveRoomRepository;
import com.sok.backend.persistence.ActiveRoomRow;
import com.sok.backend.realtime.match.BattleOrchestrator;
import com.sok.backend.realtime.match.ClaimPhaseOrchestrator;
import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomExecutorRegistry;
import com.sok.backend.realtime.room.RoomLifecycle;
import com.sok.backend.realtime.room.RoomStore;
import com.sok.backend.realtime.room.RoomTimerScheduler;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Loads durable {@code active_rooms} rows after restart, repopulates {@link RoomStore}, and
 * reschedules phase timers. Players are offline until they reconnect via {@code join_matchmaking}.
 */
@Component
public class RoomRehydrationService {

  private static final Logger log = LoggerFactory.getLogger(RoomRehydrationService.class);
  private static final String TIMER_CLAIM_Q = "claim_question_timeout";
  private static final String TIMER_DUEL = "duel_timeout";
  private static final String TIMER_TIEBREAK = "tiebreak_timeout";

  private final ActiveRoomRepository activeRoomRepository;
  private final RoomSnapshotMapper snapshotMapper;
  private final RoomStore store;
  private final RuntimeGameConfigService runtimeConfigService;
  private final SocketIOServer socketServer;
  private final RoomTimerScheduler roomTimers;
  private final RoomExecutorRegistry executors;
  private final BattleOrchestrator battleOrchestrator;
  private final ClaimPhaseOrchestrator claimPhaseOrchestrator;
  private final RoomLifecycle roomLifecycle;
  private final RoomBroadcaster broadcaster;
  private final AvoidBombsTieBreakInteractionService avoidBombsInteraction;
  private final CollectionTieBreakService collectionTieBreakService;
  private final TieBreakMinigameScheduler tieBreakMinigameScheduler;

  public RoomRehydrationService(
      ActiveRoomRepository activeRoomRepository,
      RoomSnapshotMapper snapshotMapper,
      RoomStore store,
      RuntimeGameConfigService runtimeConfigService,
      @Lazy SocketIOServer socketServer,
      RoomTimerScheduler roomTimers,
      RoomExecutorRegistry executors,
      BattleOrchestrator battleOrchestrator,
      ClaimPhaseOrchestrator claimPhaseOrchestrator,
      RoomLifecycle roomLifecycle,
      RoomBroadcaster broadcaster,
      AvoidBombsTieBreakInteractionService avoidBombsInteraction,
      CollectionTieBreakService collectionTieBreakService,
      @Lazy TieBreakMinigameScheduler tieBreakMinigameScheduler) {
    this.activeRoomRepository = activeRoomRepository;
    this.snapshotMapper = snapshotMapper;
    this.store = store;
    this.runtimeConfigService = runtimeConfigService;
    this.socketServer = socketServer;
    this.roomTimers = roomTimers;
    this.executors = executors;
    this.battleOrchestrator = battleOrchestrator;
    this.claimPhaseOrchestrator = claimPhaseOrchestrator;
    this.roomLifecycle = roomLifecycle;
    this.broadcaster = broadcaster;
    this.avoidBombsInteraction = avoidBombsInteraction;
    this.collectionTieBreakService = collectionTieBreakService;
    this.tieBreakMinigameScheduler = tieBreakMinigameScheduler;
  }

  /**
   * Loads one room from Postgres when it is missing from memory (e.g. after uid→room Redis TTL).
   *
   * @return true if a room was loaded into {@link RoomStore}.
   */
  public boolean hydrateRoomFromDbIfAbsent(String roomId) {
    if (roomId == null || store.get(roomId) != null) {
      return false;
    }
    Optional<ActiveRoomRow> row = activeRoomRepository.findByRoomId(roomId);
    return row.filter(r -> r.phase() == null || !RoomState.PHASE_ENDED.equals(r.phase()))
        .map(r -> hydrateRow(r, false))
        .orElse(false);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    GameRuntimeConfig cfg = runtimeConfigService.get();
    long maxAgeSec = (long) cfg.getMaxMatchDurationSeconds() * 2L;
    List<ActiveRoomRow> rows = activeRoomRepository.findAll();
    int loaded = 0;
    for (ActiveRoomRow row : rows) {
      if (row.phase() != null && RoomState.PHASE_ENDED.equals(row.phase())) {
        activeRoomRepository.deleteById(row.roomId());
        continue;
      }
      if (row.updatedAt() != null
          && Duration.between(row.updatedAt(), Instant.now()).getSeconds() > maxAgeSec) {
        activeRoomRepository.deleteById(row.roomId());
        continue;
      }
      String snap = row.snapshotJson();
      if (snap == null || snap.isBlank() || "{}".equals(snap.trim())) {
        activeRoomRepository.deleteById(row.roomId());
        continue;
      }
      if (hydrateRow(row, true)) {
        loaded++;
      }
    }
    if (loaded > 0) {
      log.info("sok rehydration complete loaded={} totalRows={}", loaded, rows.size());
    }
  }

  /**
   * @param deleteBadSnapshot if true, delete DB row when snapshot JSON cannot be parsed (startup
   *     cleanup).
   */
  private boolean hydrateRow(ActiveRoomRow row, boolean deleteBadSnapshot) {
    String snap = row.snapshotJson();
    RoomSnapshotDto dto;
    try {
      dto = snapshotMapper.fromJson(snap);
    } catch (RuntimeException ex) {
      log.warn("sok rehydrate skip bad snapshot roomId={} err={}", row.roomId(), ex.toString());
      if (deleteBadSnapshot) {
        activeRoomRepository.deleteById(row.roomId());
      }
      return false;
    }
    RoomState room = snapshotMapper.toRoomState(dto);
    long now = System.currentTimeMillis();
    for (PlayerState p : room.players) {
      p.online = false;
      p.socketId = null;
      p.lastSeenAt = now;
    }
    store.put(room.id, room);
    for (PlayerState p : room.players) {
      if (p.uid != null) {
        store.mapUidToRoom(p.uid, room.id);
      }
    }
    reschedulePhaseTimers(room);
    roomLifecycle.scheduleCleanup(room);
    log.info("sok rehydrated roomId={} phase={}", room.id, room.phase);
    return true;
  }

  private void reschedulePhaseTimers(RoomState room) {
    GameRuntimeConfig cfg = runtimeConfigService.get();
    long now = System.currentTimeMillis();

    if (RoomState.PHASE_CLAIM_Q.equals(room.phase)) {
      long deadline = room.phaseStartedAt + cfg.getClaimDurationMs();
      long remaining = deadline - now + 50L;
      if (remaining <= 0L) {
        submitRoom(
            room.id,
            () -> claimPhaseOrchestrator.resolveEstimationRound(socketServer, room));
      } else {
        roomTimers.scheduleTimer(
            room,
            TIMER_CLAIM_Q,
            remaining,
            () ->
                submitRoom(
                    room.id,
                    () ->
                        claimPhaseOrchestrator.resolveEstimationRound(socketServer, room)));
      }
      return;
    }

    if (RoomState.PHASE_DUEL.equals(room.phase)
        && room.activeDuel != null
        && room.activeDuel.mcqQuestion != null) {
      long deadline = room.phaseStartedAt + cfg.getDuelDurationMs();
      long remaining = deadline - now + 50L;
      if (remaining <= 0L) {
        submitRoom(
            room.id,
            () -> {
              battleOrchestrator.autoFillDuel(room.activeDuel, cfg.getDuelDurationMs());
              battleOrchestrator.resolveDuel(socketServer, room);
            });
      } else {
        roomTimers.scheduleTimer(
            room,
            TIMER_DUEL,
            remaining,
            () ->
                submitRoom(
                    room.id,
                    () -> {
                      battleOrchestrator.autoFillDuel(room.activeDuel, cfg.getDuelDurationMs());
                      battleOrchestrator.resolveDuel(socketServer, room);
                    }));
      }
      return;
    }

    if (RoomState.PHASE_TIE.equals(room.phase) && room.activeDuel != null) {
      DuelState duel = room.activeDuel;
      if ("numeric".equals(duel.tiebreakKind)) {
        long deadline = room.phaseStartedAt + cfg.getTiebreakDurationMs();
        long remaining = deadline - now + 50L;
        if (remaining <= 0L) {
          submitRoom(room.id, () -> battleOrchestrator.resolveTiebreaker(socketServer, room));
        } else {
          roomTimers.scheduleTimer(
              room,
              TIMER_TIEBREAK,
              remaining,
              () ->
                  submitRoom(
                      room.id,
                      () -> battleOrchestrator.resolveTiebreaker(socketServer, room)));
        }
        return;
      }
      if ("avoid_bombs".equals(duel.tiebreakKind)
          && "placement".equals(duel.avoidBombsSubPhase)) {
        long placementMs = Math.max(500L, cfg.getAvoidBombsPlacementMs());
        long deadline = room.phaseStartedAt + placementMs;
        long remaining = deadline - now + 50L;
        if (remaining <= 0L) {
          submitRoom(
              room.id,
              () -> {
                avoidBombsInteraction.autofillPlacementsAndStart(duel);
                socketServer
                    .getRoomOperations(room.id)
                    .sendEvent(
                        "tiebreaker_avoid_bombs_ready",
                        AvoidBombsTieBreakPayloadFactory.readyPayload(room.id, duel));
                broadcaster.emitRoomUpdate(room);
              });
        } else {
          roomTimers.scheduleTimer(
              room,
              AvoidBombsTieBreakerAttackPhaseStrategy.PLACEMENT_TIMER_KEY,
              remaining,
              () ->
                  submitRoom(
                      room.id,
                      () -> {
                        if (!"avoid_bombs".equals(duel.tiebreakKind)
                            || !"placement".equals(duel.avoidBombsSubPhase)) {
                          return;
                        }
                        avoidBombsInteraction.autofillPlacementsAndStart(duel);
                        socketServer
                            .getRoomOperations(room.id)
                            .sendEvent(
                                "tiebreaker_avoid_bombs_ready",
                                AvoidBombsTieBreakPayloadFactory.readyPayload(room.id, duel));
                        broadcaster.emitRoomUpdate(room);
                      }));
        }
      }
      if ("collection".equals(duel.tiebreakKind) && "pick".equals(duel.collectionSubPhase)) {
        long pickEnd =
            duel.collectionPickDeadlineAtMs != null
                ? duel.collectionPickDeadlineAtMs
                : room.phaseStartedAt + cfg.getCollectionPickMs();
        long remainingPick = pickEnd - now + 50L;
        final String rid = room.id;
        if (remainingPick <= 0L) {
          submitRoom(
              rid,
              () -> {
                RoomState ro = store.get(rid);
                if (ro == null || ro.activeDuel == null) return;
                DuelState d = ro.activeDuel;
                if (!"collection".equals(d.tiebreakKind) || !"pick".equals(d.collectionSubPhase)) {
                  return;
                }
                TieBreakerRealtimeBridge bridge = battleOrchestrator.tieBreakerBridge(socketServer, ro);
                collectionTieBreakService.onPickDeadline(d, bridge);
                broadcaster.emitRoomUpdate(ro);
              });
        } else {
          roomTimers.scheduleTimer(
              room,
              CollectionTieBreakService.COLLECTION_PICK_TIMER_KEY,
              remainingPick,
              () ->
                  submitRoom(
                      rid,
                      () -> {
                        RoomState ro = store.get(rid);
                        if (ro == null || ro.activeDuel == null) return;
                        DuelState d = ro.activeDuel;
                        if (!"collection".equals(d.tiebreakKind)
                            || !"pick".equals(d.collectionSubPhase)) {
                          return;
                        }
                        TieBreakerRealtimeBridge bridge =
                            battleOrchestrator.tieBreakerBridge(socketServer, ro);
                        collectionTieBreakService.onPickDeadline(d, bridge);
                        broadcaster.emitRoomUpdate(ro);
                      }));
        }
      }
      if ("rhythm".equals(duel.tiebreakKind)) {
        tieBreakMinigameScheduler.scheduleRhythmRoundDeadline(room.id);
      }
      if ("memory".equals(duel.tiebreakKind) && "peek".equals(duel.memorySubPhase)) {
        tieBreakMinigameScheduler.scheduleMemoryPeekEnd(room.id);
      }
    }
  }

  private void submitRoom(String roomId, Runnable task) {
    if (!executors.submitToRoom(roomId, task)) {
      log.warn("sok rehydrate timer submit rejected roomId={}", roomId);
    }
  }
}
