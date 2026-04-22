package com.sok.backend.realtime.match;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.domain.game.ResolutionPhaseService;
import com.sok.backend.realtime.room.RoomClientSnapshotFactory;
import com.sok.backend.realtime.persistence.RoomSnapshotCoordinator;
import com.sok.backend.realtime.room.RoomLifecycle;
import com.sok.backend.realtime.room.RoomRulesResolver;
import com.sok.backend.service.ProgressionService;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

import static com.sok.backend.realtime.room.RoomClientSnapshotFactory.mapOf;

/** Evaluates end-of-match conditions and finalizes completed games. */
@Component
public class MatchOutcomeService {
  private static final String PHASE_ENDED = "ended";
  private static final String PHASE_WAITING = "waiting";
  private static final String MODE_TEAMS_2V2 = "teams_2v2";

  private final RuntimeGameConfigService runtimeConfigService;
  private final ResolutionPhaseService resolutionPhaseService;
  private final ProgressionService progressionService;
  private final RoomClientSnapshotFactory snapshotFactory;
  private final RoomLifecycle lifecycle;
  private final RoomRulesResolver rulesResolver;
  private final RoomSnapshotCoordinator snapshotCoordinator;

  public MatchOutcomeService(
      RuntimeGameConfigService runtimeConfigService,
      ResolutionPhaseService resolutionPhaseService,
      ProgressionService progressionService,
      RoomClientSnapshotFactory snapshotFactory,
      RoomLifecycle lifecycle,
      RoomRulesResolver rulesResolver,
      RoomSnapshotCoordinator snapshotCoordinator) {
    this.runtimeConfigService = runtimeConfigService;
    this.resolutionPhaseService = resolutionPhaseService;
    this.progressionService = progressionService;
    this.snapshotFactory = snapshotFactory;
    this.lifecycle = lifecycle;
    this.rulesResolver = rulesResolver;
    this.snapshotCoordinator = snapshotCoordinator;
  }

  public void evaluateEndConditions(SocketIOServer server, RoomState room) {
    if (PHASE_ENDED.equals(room.phase)) return;
    if (PHASE_WAITING.equals(room.phase)) return;
    List<PlayerState> alive = new ArrayList<>();
    for (PlayerState p : room.players) {
      if (!p.isEliminated) alive.add(p);
    }
    if (MODE_TEAMS_2V2.equals(room.matchMode)) {
      HashSet<String> teamsAlive = new HashSet<>();
      for (PlayerState p : alive) {
        if (p.teamId != null) teamsAlive.add(p.teamId);
      }
      if (teamsAlive.size() == 1 && !alive.isEmpty()) {
        String winningTeam = teamsAlive.iterator().next();
        finishGame(server, room, rulesResolver.topScorerOnTeam(room, winningTeam), "team_survival");
        return;
      }
    }
    if (alive.size() == 1) {
      finishGame(server, room, alive.get(0).uid, "domination");
      return;
    }
    GameRuntimeConfig cfg = runtimeConfigService.get();
    long elapsedSec = (System.currentTimeMillis() - room.matchStartedAt) / 1000L;
    if (room.round >= cfg.getMaxRounds() || elapsedSec >= cfg.getMaxMatchDurationSeconds()) {
      finishGame(server, room, topScorer(room), "threshold");
    }
  }

  public void finishGame(SocketIOServer server, RoomState room, String winnerUid, String reason) {
    room.phase = PHASE_ENDED;
    HashMap<String, Object> payload = new HashMap<>();
    payload.put("winnerUid", winnerUid);
    payload.put("reason", reason);
    List<Map<String, Object>> ranks = rankings(room, winnerUid);
    payload.put("rankings", ranks);
    payload.put("room", snapshotFactory.roomToClient(room));
    server.getRoomOperations(room.id).sendEvent("game_ended", payload);
    snapshotCoordinator.removeRoom(room.id);
    int place = 1;
    for (Map<String, Object> row : ranks) {
      String uid = String.valueOf(row.get("uid"));
      int p = row.get("place") instanceof Number ? ((Number) row.get("place")).intValue() : place;
      progressionService.grantMatchResult(uid, p, room.id + ":" + room.matchStartedAt);
      place++;
    }
    lifecycle.scheduleRoomShutdown(room.id, runtimeConfigService.get().getReconnectGraceSeconds());
  }

  public String topScorer(RoomState room) {
    String winner = resolutionPhaseService.topScorer(room.scoreByUid);
    return winner == null && !room.players.isEmpty() ? room.players.get(0).uid : winner;
  }

  public List<Map<String, Object>> rankings(RoomState room, String winnerUid) {
    List<Map<String, Object>> out = new ArrayList<>();
    out.add(mapOf("uid", winnerUid, "place", 1));
    int place = 2;
    for (PlayerState p : room.players) {
      if (!winnerUid.equals(p.uid)) {
        out.add(mapOf("uid", p.uid, "place", place++));
      }
    }
    return out;
  }
}
