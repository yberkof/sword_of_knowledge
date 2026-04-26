package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.realtime.match.BattleOrchestrator;
import com.sok.backend.realtime.match.ClaimPhaseOrchestrator;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RegionState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomClientSnapshotFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.sok.backend.realtime.room.RoomClientSnapshotFactory.mapOf;

/** Server path for {@code claim_region}. */
@Component
public class ClaimRegionEventHandler {
  private static final Logger log = LoggerFactory.getLogger(ClaimRegionEventHandler.class);

  private final BattleOrchestrator battle;
  private final ClaimPhaseOrchestrator claimPhase;
  private final RoomClientSnapshotFactory snapshotFactory;
  private final RoomBroadcaster broadcaster;

  public ClaimRegionEventHandler(
      BattleOrchestrator battle,
      ClaimPhaseOrchestrator claimPhase,
      RoomClientSnapshotFactory snapshotFactory,
      RoomBroadcaster broadcaster) {
    this.battle = battle;
    this.claimPhase = claimPhase;
    this.snapshotFactory = snapshotFactory;
    this.broadcaster = broadcaster;
  }

  public void onClaim(
      SocketIOClient client,
      SocketIOServer server,
      String roomId,
      String uid,
      int regionId,
      RoomState room) {
    if (room == null || !GamePhases.CLAIMING_PICK.equals(room.phase)) {
      log.warn(
          "sok claim_region ignored: room or phase roomId={} uid={} phase={}",
          roomId,
          uid,
          room == null ? "null" : room.phase);
      return;
    }
    if (!uid.equals(room.claimTurnUid)) {
      log.warn(
          "sok claim_region ignored: not your turn roomId={} uid={} claimTurnUid={}",
          roomId,
          uid,
          room.claimTurnUid);
      return;
    }
    Integer left = room.claimPicksLeftByUid.get(uid);
    if (left == null || left <= 0) {
      log.warn("sok claim_region ignored: no picks left roomId={} uid={}", roomId, uid);
      return;
    }
    RegionState r = room.regions.get(regionId);
    if (r == null || r.ownerUid != null) {
      log.warn(
          "sok claim_region ignored: bad hex or already owned roomId={} uid={} regionId={} hasRegion={} owner={}",
          roomId,
          uid,
          regionId,
          r != null,
          r == null ? null : r.ownerUid);
      return;
    }
    if (!battle.canAttackRegion(room, uid, regionId)) {
      log.warn("sok claim_region rejected: not adjacent roomId={} uid={} regionId={}", roomId, uid, regionId);
      client.sendEvent("claim_rejected", mapOf("reason", "not_adjacent"));
      return;
    }
    r.ownerUid = uid;
    PlayerState p = room.playersByUid.get(uid);
    p.score += snapshotFactory.pointValue(room, regionId);
    room.scoreByUid.put(uid, p.score);
    room.claimPicksLeftByUid.put(uid, left - 1);
    if (left - 1 <= 0) {
      claimPhase.rotateClaimTurn(room);
    }
    log.info(
        "sok claim_region ok roomId={} uid={} regionId={} picksLeftForUid={} neutralRemaining={}",
        roomId,
        uid,
        regionId,
        room.claimPicksLeftByUid.get(uid),
        claimPhase.countNeutralRegions(room));
    broadcaster.emitRoomUpdate(room);
    if (claimPhase.allRegionsClaimed(room)) {
      log.info("sok all regions claimed → battle roomId={}", roomId);
      battle.startBattlePhase(server, room);
    } else if (claimPhase.claimsQueueEmpty(room)) {
      log.info(
          "sok claim queue empty, neutralRemaining={} → next estimation roomId={}",
          claimPhase.countNeutralRegions(room),
          roomId);
      claimPhase.startClaimingQuestionRound(server, room);
    }
  }
}
