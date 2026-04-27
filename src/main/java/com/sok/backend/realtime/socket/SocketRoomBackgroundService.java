package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.config.RealtimeScaleProperties;
import com.sok.backend.realtime.RoomRegistryService;
import com.sok.backend.realtime.match.MatchOutcomeService;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.persistence.RoomSnapshotCoordinator;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomLifecycle;
import com.sok.backend.realtime.room.RoomSerialCommandService;
import com.sok.backend.realtime.room.RoomStore;
import com.sok.backend.realtime.room.RoomTimerScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Disconnect listener and periodic room maintenance (moved from {@code SocketGateway}).
 */
@Component
public class SocketRoomBackgroundService {

  private final ObjectProvider<RoomRegistryService> roomRegistry;
  private final RealtimeScaleProperties scale;
  private final AtomicLong lastSnapshotTickMs = new AtomicLong(0L);
  private final RoomSnapshotCoordinator snapshotCoordinator;
  private final RoomStore store;
  private final RoomSerialCommandService roomCommands;
  private final RoomTimerScheduler roomTimers;
  private final RoomBroadcaster broadcaster;
  private final MatchOutcomeService matchOutcome;
  private final RoomLifecycle lifecycle;

  public SocketRoomBackgroundService(
      ObjectProvider<RoomRegistryService> roomRegistry,
      RealtimeScaleProperties scale,
      RoomSnapshotCoordinator snapshotCoordinator,
      RoomStore store,
      RoomSerialCommandService roomCommands,
      RoomTimerScheduler roomTimers,
      RoomBroadcaster broadcaster,
      MatchOutcomeService matchOutcome,
      RoomLifecycle lifecycle) {
    this.roomRegistry = roomRegistry;
    this.scale = scale;
    this.snapshotCoordinator = snapshotCoordinator;
    this.store = store;
    this.roomCommands = roomCommands;
    this.roomTimers = roomTimers;
    this.broadcaster = broadcaster;
    this.matchOutcome = matchOutcome;
    this.lifecycle = lifecycle;
  }

  public void register(SocketIOServer server) {
    server.addDisconnectListener(
        client -> {
          Object uidObj = client.get("uid");
          if (uidObj == null) {
            return;
          }
          String uid = String.valueOf(uidObj);
          String roomId = store.roomIdForUid(uid);
          if (roomId == null) {
            return;
          }
          roomCommands.runInRoom(
              roomId,
              room -> {
                PlayerState p = room.playersByUid.get(uid);
                if (p == null) {
                  return;
                }
                if (GamePhases.WAITING.equals(room.phase)
                    || RoomState.PHASE_ENDED.equals(room.phase)) {
                  lifecycle.removePlayerFromRoom(room, uid);
                  return;
                }
                p.online = false;
                p.lastSeenAt = System.currentTimeMillis();
                lifecycle.scheduleCleanup(room);
                broadcaster.emitRoomUpdate(room);
              });
        });

    roomTimers.scheduleAtFixedRate(
        new Runnable() {
          @Override
          public void run() {
            final boolean hotThisTick = shouldRunHotSnapshotThisTick();
            final RoomRegistryService reg = roomRegistry.getIfAvailable();
            List<String> roomIds = new ArrayList<>(store.rooms().keySet());
            for (String roomId : roomIds) {
              final String rid = roomId;
              roomCommands.runInRoom(
                  rid,
                  room -> {
                    if (reg != null) {
                      reg.refresh(rid);
                    }
                    matchOutcome.evaluateEndConditions(server, room);
                    lifecycle.evictDisconnectedPlayers(room);
                    if (hotThisTick) {
                      snapshotCoordinator.snapshotHot(room);
                    }
                  });
            }
          }
        },
        5,
        5,
        TimeUnit.SECONDS);
  }

  /**
   * At most one timer fire in {@code interval} advances the window; that fire publishes hot
   * snapshots (still executed per room on the room executor, not on the timer thread).
   */
  private boolean shouldRunHotSnapshotThisTick() {
    if (!scale.isSnapshotToRedis()) {
      return false;
    }
    long interval = scale.getSnapshotIntervalMs();
    if (interval <= 0L) {
      return false;
    }
    long now = System.currentTimeMillis();
    long prev = lastSnapshotTickMs.get();
    if (now - prev < interval) {
      return false;
    }
    return lastSnapshotTickMs.compareAndSet(prev, now);
  }
}
