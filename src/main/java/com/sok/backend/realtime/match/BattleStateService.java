package com.sok.backend.realtime.match;

import com.sok.backend.realtime.room.RoomClientSnapshotFactory;
import com.sok.backend.realtime.room.RoomRulesResolver;
import org.springframework.stereotype.Component;

/**
 * Manages domain state transitions for the battle phase.
 */
@Component
public class BattleStateService {

  private final RoomClientSnapshotFactory snapshotFactory;
  private final RoomRulesResolver rulesResolver;

  public BattleStateService(
      RoomClientSnapshotFactory snapshotFactory,
      RoomRulesResolver rulesResolver) {
    this.snapshotFactory = snapshotFactory;
    this.rulesResolver = rulesResolver;
  }

  public boolean canAttackRegion(RoomState room, String attackerUid, int targetRegionId) {
    RegionState target = room.regions.get(targetRegionId);
    if (target == null) return false;
    String ownerUid = target.ownerUid;
    if (ownerUid != null && rulesResolver.sameTeam(room, attackerUid, ownerUid)) return false;
    for (Integer n : target.neighbors) {
      RegionState near = room.regions.get(n);
      if (near != null && attackerUid.equals(near.ownerUid)) return true;
    }
    return false;
  }

  public void applyAttackerCapture(RoomState room, DuelState duel) {
    PlayerState attacker = room.getPlayer(duel.attackerUid);
    PlayerState defender = room.getPlayer(duel.defenderUid);
    RegionState hex = room.getRegion(duel.targetRegionId);
    if (attacker == null || hex == null) return;
    if (hex.isCastle && defender != null) {
      defender.damageCastle();
      if (defender.isDead()) {
        defender.eliminate();
        for (RegionState h : room.regions.values()) {
          if (defender.uid.equals(h.ownerUid)) {
            h.ownerUid = attacker.uid;
            attacker.score += snapshotFactory.pointValue(room, h.id);
          }
        }
        room.updateScore(attacker.uid, attacker.score);
      }
      return;
    }
    hex.ownerUid = attacker.uid;
    hex.type = "player";
  }

  public void advanceTurnAndRound(RoomState room) {
    advanceTurnSkipEliminated(room);
    room.roundAttackCount++;
    if (room.roundAttackCount >= room.players.size()) {
      room.roundAttackCount = 0;
      room.round++;
    }
  }

  public void advanceTurnSkipEliminated(RoomState room) {
    int n = room.players.size();
    if (n == 0) return;
    for (int i = 0; i < n; i++) {
      room.currentTurnIndex = (room.currentTurnIndex + 1) % n;
      if (!room.players.get(room.currentTurnIndex).isEliminated) return;
    }
  }

  public int firstAliveIndex(RoomState room) {
    for (int i = 0; i < room.players.size(); i++) {
      if (!room.players.get(i).isEliminated) return i;
    }
    return 0;
  }
}
