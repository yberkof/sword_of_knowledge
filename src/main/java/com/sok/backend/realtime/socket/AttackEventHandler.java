package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.realtime.match.BattleOrchestrator;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RegionState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.room.RoomRulesResolver;
import org.springframework.stereotype.Component;

import static com.sok.backend.realtime.room.RoomClientSnapshotFactory.mapOf;

/** Server path for {@code attack}. */
@Component
public class AttackEventHandler {

  private final BattleOrchestrator battle;
  private final RoomRulesResolver rulesResolver;

  public AttackEventHandler(BattleOrchestrator battle, RoomRulesResolver rulesResolver) {
    this.battle = battle;
    this.rulesResolver = rulesResolver;
  }

  public void onAttack(
      SocketIOClient client,
      SocketIOServer server,
      String roomId,
      String attackerUid,
      int targetHexId,
      RoomState room) {
    if (room == null) {
      client.sendEvent("attack_invalid", mapOf("reason", "no_room"));
      return;
    }
    if (!GamePhases.BATTLE.equals(room.phase)) {
      client.sendEvent("attack_invalid", mapOf("reason", "bad_phase"));
      return;
    }
    PlayerState turn = room.players.get(room.currentTurnIndex);
    if (!attackerUid.equals(turn.uid)) {
      client.sendEvent("attack_invalid", mapOf("reason", "not_your_turn"));
      return;
    }
    RegionState targetHex = room.regions.get(targetHexId);
    if (targetHex == null) {
      client.sendEvent("attack_invalid", mapOf("reason", "bad_hex"));
      return;
    }
    if (attackerUid.equals(targetHex.ownerUid)) {
      client.sendEvent("attack_invalid", mapOf("reason", "own_territory"));
      return;
    }
    String defenderUid = targetHex.ownerUid;
    if (defenderUid != null && rulesResolver.sameTeam(room, attackerUid, defenderUid)) {
      client.sendEvent("attack_invalid", mapOf("reason", "ally_territory"));
      return;
    }
    if (!battle.canAttackRegion(room, attackerUid, targetHexId)) {
      client.sendEvent("attack_invalid", mapOf("reason", "not_adjacent"));
      return;
    }
    room.mcqSpeedTieRetries = 0;
    room.tieBreakOverride = null;
    battle.startDuel(server, room, attackerUid, targetHexId, false);
  }
}
