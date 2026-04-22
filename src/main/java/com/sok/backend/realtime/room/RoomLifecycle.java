package com.sok.backend.realtime.room;

import com.sok.backend.realtime.RoomRegistryService;
import com.sok.backend.realtime.persistence.RoomSnapshotCoordinator;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.matchmaking.MatchmakingAllocator;
import com.sok.backend.service.config.RuntimeGameConfigService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Player/room teardown: disconnect cleanup, eviction after grace period, and shutdown (remove
 * in-memory state, cancel timers, clear matchmaking indexes, notify Redis mirrors).
 */
@Component
public class RoomLifecycle {
  public static final String PHASE_WAITING = "waiting";
  private static final String DISCONNECT_CLEANUP_TIMER = "disconnect_cleanup";

  private final RoomStore store;
  private final RoomExecutorRegistry roomExecutors;
  private final RoomTimerScheduler roomTimers;
  private final RoomBroadcaster broadcaster;
  private final MatchmakingAllocator matchmakingAllocator;
  private final RuntimeGameConfigService runtimeConfigService;
  private final ObjectProvider<RoomRegistryService> roomRegistry;
  private final RoomSnapshotCoordinator snapshotCoordinator;

  public RoomLifecycle(
      RoomStore store,
      RoomExecutorRegistry roomExecutors,
      RoomTimerScheduler roomTimers,
      RoomBroadcaster broadcaster,
      MatchmakingAllocator matchmakingAllocator,
      RuntimeGameConfigService runtimeConfigService,
      ObjectProvider<RoomRegistryService> roomRegistry,
      RoomSnapshotCoordinator snapshotCoordinator) {
    this.store = store;
    this.roomExecutors = roomExecutors;
    this.roomTimers = roomTimers;
    this.broadcaster = broadcaster;
    this.matchmakingAllocator = matchmakingAllocator;
    this.runtimeConfigService = runtimeConfigService;
    this.roomRegistry = roomRegistry;
    this.snapshotCoordinator = snapshotCoordinator;
  }

  public void removePlayerFromRoom(RoomState room, String uid) {
    PlayerState removed = room.playersByUid.remove(uid);
    if (removed == null) {
      return;
    }
    store.unmapUid(uid);
    List<PlayerState> next = new ArrayList<>();
    for (PlayerState p : room.players) {
      if (!uid.equals(p.uid)) {
        next.add(p);
      }
    }
    room.players = next;
    if (room.players.isEmpty()) {
      shutdownRoom(room.id);
      return;
    }
    room.hostUid = room.players.get(0).uid;
    if (room.players.size() == 1
        && PHASE_WAITING.equals(room.phase)
        && room.inviteCode == null) {
      matchmakingAllocator.markAsSoloPublicWaiting(room.id);
    }
    broadcaster.emitRoomUpdate(room);
  }

  public void scheduleCleanup(RoomState room) {
    roomTimers.scheduleTimer(
        room,
        DISCONNECT_CLEANUP_TIMER,
        runtimeConfigService.get().getReconnectGraceSeconds() * 1000L,
        () -> evictDisconnectedPlayers(room));
  }

  public void evictDisconnectedPlayers(RoomState room) {
    long now = System.currentTimeMillis();
    int graceMs = runtimeConfigService.get().getReconnectGraceSeconds() * 1000;
    List<String> toRemove = new ArrayList<>();
    for (PlayerState p : room.players) {
      if (!p.online && now - p.lastSeenAt >= graceMs) toRemove.add(p.uid);
    }
    for (String uid : toRemove) removePlayerFromRoom(room, uid);
  }

  public void scheduleRoomShutdown(String roomId, int seconds) {
    roomTimers.schedule(() -> shutdownRoom(roomId), seconds, TimeUnit.SECONDS);
  }

  public void shutdownRoom(String roomId) {
    RoomState room = store.remove(roomId);
    if (room != null) {
      for (String uid : room.playersByUid.keySet()) store.unmapUid(uid);
      roomTimers.cancelAll(room);
      matchmakingAllocator.clearIndexesForRoom(room);
    }
    roomExecutors.removeRoomLock(roomId);
    RoomRegistryService reg = roomRegistry.getIfAvailable();
    if (reg != null) {
      reg.remove(roomId);
    }
    snapshotCoordinator.removeRoom(roomId);
  }
}
