package com.sok.backend.realtime.matchmaking;

import com.sok.backend.config.RealtimeScaleProperties;
import com.sok.backend.realtime.RoomRegistryService;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.room.RoomStateFactory;
import com.sok.backend.realtime.room.RoomStore;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Ownership of waiting-lobby placement: which room a joining player lands in, invite-code lookup,
 * and the shared lock that guards new-room creation.
 */
@Component
public class MatchmakingAllocator {
  public static final String PHASE_WAITING = "waiting";

  /** Result of a placement decision: the assigned room id and whether we just created it empty. */
  public record Allocation(String roomId, boolean brandNewEmpty) {}

  private final RoomStore store;
  private final RoomStateFactory stateFactory;
  private final RuntimeGameConfigService runtimeConfigService;
  private final RealtimeScaleProperties scale;
  private final ObjectProvider<RoomRegistryService> roomRegistry;

  private final Object matchmakingLock = new Object();
  private final ConcurrentHashMap<String, String> waitingInviteToRoomId = new ConcurrentHashMap<>();
  private volatile String soloPublicWaitingRoomId;

  public MatchmakingAllocator(
      RoomStore store,
      RoomStateFactory stateFactory,
      RuntimeGameConfigService runtimeConfigService,
      RealtimeScaleProperties scale,
      ObjectProvider<RoomRegistryService> roomRegistry) {
    this.store = store;
    this.stateFactory = stateFactory;
    this.runtimeConfigService = runtimeConfigService;
    this.scale = scale;
    this.roomRegistry = roomRegistry;
  }

  /**
   * Resolve an existing waiting room or create a new one. Returns {@code null} when the server is
   * at its {@code maxRooms} capacity.
   */
  public Allocation findOrCreateRoomAllocation(String normalizedInvite) {
    GameRuntimeConfig cfg = runtimeConfigService.get();
    synchronized (matchmakingLock) {
      if (scale.getMaxRooms() > 0 && store.size() >= scale.getMaxRooms()) {
        return null;
      }
      if (normalizedInvite != null && normalizedInvite.length() >= 4) {
        String idByInvite = waitingInviteToRoomId.get(normalizedInvite);
        if (idByInvite != null) {
          RoomState existing = store.get(idByInvite);
          if (existing != null
              && PHASE_WAITING.equals(existing.phase)
              && normalizedInvite.equals(existing.inviteCode)
              && existing.players.size() < cfg.getMaxPlayers()) {
            return new Allocation(idByInvite, false);
          }
          waitingInviteToRoomId.remove(normalizedInvite, idByInvite);
        }
        String id = store.newRoomId();
        RoomState room = newWaitingRoom(cfg, id, normalizedInvite);
        store.put(id, room);
        waitingInviteToRoomId.put(normalizedInvite, id);
        touchRoomRegistry(id);
        return new Allocation(id, true);
      }

      // Public queue: include the indexed room even when players.size() == 0 — the first member is
      // added asynchronously (submitToRoom), so requiring size == 1 caused a second client to clear
      // soloPublicWaitingRoomId and open a duplicate waiting room.
      if (soloPublicWaitingRoomId != null) {
        RoomState solo = store.get(soloPublicWaitingRoomId);
        if (solo != null
            && PHASE_WAITING.equals(solo.phase)
            && solo.inviteCode == null
            && solo.players.size() < cfg.getMaxPlayers()) {
          return new Allocation(soloPublicWaitingRoomId, false);
        }
        soloPublicWaitingRoomId = null;
      }

      RoomState bestPublicWaiting = null;
      for (RoomState room : store.values()) {
        if (PHASE_WAITING.equals(room.phase)
            && room.inviteCode == null
            && room.players.size() < cfg.getMaxPlayers()
            && (bestPublicWaiting == null
                || room.players.size() > bestPublicWaiting.players.size())) {
          bestPublicWaiting = room;
        }
      }
      if (bestPublicWaiting != null) {
        soloPublicWaitingRoomId = bestPublicWaiting.id;
        return new Allocation(bestPublicWaiting.id, false);
      }

      String id = store.newRoomId();
      RoomState room = newWaitingRoom(cfg, id, null);
      store.put(id, room);
      soloPublicWaitingRoomId = id;
      touchRoomRegistry(id);
      return new Allocation(id, true);
    }
  }

  /** If a brand-new-empty allocation never received its first player, tear the room down. */
  public boolean isOrphanWaitingRoom(String roomId, boolean brandNewEmpty) {
    if (!brandNewEmpty) {
      return false;
    }
    RoomState r = store.get(roomId);
    return r != null && r.players.isEmpty();
  }

  public void updateSoloPublicIndexAfterJoin(RoomState room) {
    if (room.inviteCode != null) {
      return;
    }
    synchronized (matchmakingLock) {
      if (soloPublicWaitingRoomId != null && soloPublicWaitingRoomId.equals(room.id)) {
        GameRuntimeConfig cfg = runtimeConfigService.get();
        if (room.players.size() >= cfg.getMaxPlayers()) {
          soloPublicWaitingRoomId = null;
        }
      }
    }
  }

  public void clearIndexesForRoom(RoomState room) {
    if (room == null) {
      return;
    }
    synchronized (matchmakingLock) {
      if (room.inviteCode != null) {
        waitingInviteToRoomId.remove(room.inviteCode, room.id);
      }
      if (room.id.equals(soloPublicWaitingRoomId)) {
        soloPublicWaitingRoomId = null;
      }
    }
  }

  /**
   * Re-elect this room as the public solo index when it drops back to a 1-player waiting lobby.
   * Used by {@link com.sok.backend.realtime.room.RoomLifecycle} on player removal.
   */
  public void markAsSoloPublicWaiting(String roomId) {
    synchronized (matchmakingLock) {
      soloPublicWaitingRoomId = roomId;
    }
  }

  private RoomState newWaitingRoom(GameRuntimeConfig cfg, String id, String inviteCode) {
    RoomState room = stateFactory.newRoomStateWithDefaults(cfg);
    room.id = id;
    room.phase = PHASE_WAITING;
    room.inviteCode = inviteCode;
    room.regions = stateFactory.buildRegionsFromConfig(cfg);
    room.currentTurnIndex = 0;
    room.createdAt = System.currentTimeMillis();
    room.lastActivityAt = room.createdAt;
    return room;
  }

  private void touchRoomRegistry(String roomId) {
    RoomRegistryService reg = roomRegistry.getIfAvailable();
    if (reg != null) {
      reg.refresh(roomId);
    }
  }
}
