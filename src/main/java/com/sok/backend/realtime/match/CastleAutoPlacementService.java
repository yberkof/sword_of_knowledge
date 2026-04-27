package com.sok.backend.realtime.match;

import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import com.sok.backend.realtime.room.RoomClientSnapshotFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * When exactly one non-eliminated player has not placed a castle, places on
 * {@code GameRuntimeConfig#castleIndices} ({@code castle_indecies} in JSON) for that seat when the
 * hex is free; otherwise picks a random free region. Same scoring as manual {@code place_castle}.
 */
@Component
public class CastleAutoPlacementService {
  private static final String PHASE_CASTLE = "castle_placement";

  private final RuntimeGameConfigService runtimeConfigService;
  private final RoomClientSnapshotFactory snapshotFactory;
  private final RoomBroadcaster broadcaster;
  private final Random random = new Random();

  public CastleAutoPlacementService(
      RuntimeGameConfigService runtimeConfigService,
      RoomClientSnapshotFactory snapshotFactory,
      RoomBroadcaster broadcaster) {
    this.runtimeConfigService = runtimeConfigService;
    this.snapshotFactory = snapshotFactory;
    this.broadcaster = broadcaster;
  }

  public void tryAutoPlaceIfSingleRemaining(RoomState room, BattleOrchestrator battle) {
    if (room == null || !PHASE_CASTLE.equals(room.phase)) return;
    GameRuntimeConfig cfgSnap = runtimeConfigService.get();
    if (!cfgSnap.isAutoPlaceLastUnplacedCastle()) return;
    PlayerState pending = onlyUnplacedActive(room);
    if (pending == null) return;
    if (room.currentTurnIndex < 0 || room.currentTurnIndex >= room.players.size()) return;
    if (!room.players.get(room.currentTurnIndex).uid.equals(pending.uid)) return;
    int pendingIdx = -1;
    for (int i = 0; i < room.players.size(); i++) {
      if (room.players.get(i) == pending) {
        pendingIdx = i;
        break;
      }
    }
    Integer preferred = null;
    if (pendingIdx >= 0
        && cfgSnap.getCastleIndices() != null
        && pendingIdx < cfgSnap.getCastleIndices().size()) {
      preferred = cfgSnap.getCastleIndices().get(pendingIdx);
    }
    int regionId = resolveAutoCastleRegion(room, preferred);
    if (regionId < 0) return;
    RegionState region = room.regions.get(regionId);
    region.ownerUid = pending.uid;
    region.isCastle = true;
    pending.castleRegionId = regionId;
    pending.score += snapshotFactory.pointValue(room, regionId);
    room.scoreByUid.put(pending.uid, pending.score);
    battle.advanceTurnSkipEliminated(room);
    broadcaster.emitRoomUpdate(room);
  }

  private static PlayerState onlyUnplacedActive(RoomState room) {
    PlayerState out = null;
    for (PlayerState p : room.players) {
      if (p.isEliminated) continue;
      if (p.castleRegionId != null) continue;
      if (out != null) return null;
      out = p;
    }
    return out;
  }

  /**
   * Uses configured hex when it exists, is empty, and is not already someone else's castle; otherwise
   * picks a random free region (legacy behaviour).
   */
  private int resolveAutoCastleRegion(RoomState room, Integer preferredHexId) {
    if (preferredHexId != null) {
      RegionState pref = room.regions.get(preferredHexId);
      if (pref != null && pref.ownerUid == null && !pref.isCastle) {
        return preferredHexId;
      }
    }
    List<Integer> free = new ArrayList<Integer>();
    for (RegionState r : room.regions.values()) {
      if (r != null && r.ownerUid == null && !r.isCastle) {
        free.add(r.id);
      }
    }
    if (free.isEmpty()) return -1;
    Collections.shuffle(free, random);
    return free.get(0);
  }
}
