package com.sok.backend.realtime.match;

import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.service.config.RuntimeGameConfigService;
import com.sok.backend.realtime.room.RoomClientSnapshotFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * When exactly one non-eliminated player has not placed a castle, picks a random free region and
 * applies the same scoring as a manual {@code place_castle}.
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
    if (!runtimeConfigService.get().isAutoPlaceLastUnplacedCastle()) return;
    PlayerState pending = onlyUnplacedActive(room);
    if (pending == null) return;
    if (room.currentTurnIndex < 0 || room.currentTurnIndex >= room.players.size()) return;
    if (!room.players.get(room.currentTurnIndex).uid.equals(pending.uid)) return;
    List<Integer> free = new ArrayList<Integer>();
    for (RegionState r : room.regions.values()) {
      if (r != null && r.ownerUid == null) {
        free.add(r.id);
      }
    }
    if (free.isEmpty()) return;
    Collections.shuffle(free, random);
    int regionId = free.get(0);
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
}
