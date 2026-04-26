package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.realtime.match.BattleOrchestrator;
import com.sok.backend.realtime.match.CastleAutoPlacementService;
import com.sok.backend.realtime.match.CastlePlacementOrchestrator;
import com.sok.backend.realtime.match.ClaimPhaseOrchestrator;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RegionState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomClientSnapshotFactory;
import org.springframework.stereotype.Component;

/**
 * Server-authoritative {@code place_castle} and auto-castle for the last unplaced player.
 */
@Component
public class PlaceCastleEventHandler {

  private final BattleOrchestrator battle;
  private final CastlePlacementOrchestrator castlePlacement;
  private final ClaimPhaseOrchestrator claimPhase;
  private final RoomClientSnapshotFactory snapshotFactory;
  private final CastleAutoPlacementService castleAutoPlacement;
  private final RoomBroadcaster broadcaster;

  public PlaceCastleEventHandler(
      BattleOrchestrator battle,
      CastlePlacementOrchestrator castlePlacement,
      ClaimPhaseOrchestrator claimPhase,
      RoomClientSnapshotFactory snapshotFactory,
      CastleAutoPlacementService castleAutoPlacement,
      RoomBroadcaster broadcaster) {
    this.battle = battle;
    this.castlePlacement = castlePlacement;
    this.claimPhase = claimPhase;
    this.snapshotFactory = snapshotFactory;
    this.castleAutoPlacement = castleAutoPlacement;
    this.broadcaster = broadcaster;
  }

  public void onPlace(String roomId, String uid, int regionId, SocketIOServer server, RoomState room) {
    if (room == null || !GamePhases.CASTLE_PLACEMENT.equals(room.phase)) {
      return;
    }
    if (!room.regions.containsKey(regionId)) {
      return;
    }
    RegionState region = room.regions.get(regionId);
    if (region.ownerUid != null) {
      return;
    }
    if (room.currentTurnIndex < 0 || room.currentTurnIndex >= room.players.size()) {
      return;
    }
    if (!room.players.get(room.currentTurnIndex).uid.equals(uid)) {
      return;
    }
    PlayerState p = room.playersByUid.get(uid);
    if (p == null || p.castleRegionId != null) {
      return;
    }
    region.ownerUid = uid;
    region.isCastle = true;
    p.castleRegionId = regionId;
    p.score += snapshotFactory.pointValue(room, regionId);
    room.scoreByUid.put(uid, p.score);
    battle.advanceTurnSkipEliminated(room);
    broadcaster.emitRoomUpdate(room);
    if (castlePlacement.allPlayersPlacedCastle(room)) {
      claimPhase.startClaimingQuestionRound(server, room);
    } else {
      castleAutoPlacement.tryAutoPlaceIfSingleRemaining(room, battle);
      if (castlePlacement.allPlayersPlacedCastle(room)) {
        claimPhase.startClaimingQuestionRound(server, room);
      }
    }
  }
}
