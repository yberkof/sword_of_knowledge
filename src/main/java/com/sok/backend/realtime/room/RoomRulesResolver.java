package com.sok.backend.realtime.room;

import com.sok.backend.domain.game.engine.DefaultGameMode;
import com.sok.backend.domain.game.engine.GameMode;
import com.sok.backend.domain.game.engine.GameModeRegistry;
import com.sok.backend.domain.game.engine.ModeRules;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.service.config.GameRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bridges the runtime room state and the pluggable {@link GameMode} engine: resolves the effective
 * {@link ModeRules}, validates team composition, and answers team-membership questions used by
 * friendly-fire and matchmaking checks.
 */
@Component
public class RoomRulesResolver {
  private static final Logger log = LoggerFactory.getLogger(RoomRulesResolver.class);

  private final GameModeRegistry gameModeRegistry;
  private final DefaultGameMode defaultGameMode;

  public RoomRulesResolver(GameModeRegistry gameModeRegistry, DefaultGameMode defaultGameMode) {
    this.gameModeRegistry = gameModeRegistry;
    this.defaultGameMode = defaultGameMode;
  }

  /** Resolves the mode for a room, falling back to {@code sok_v1} when the rulesetId is unknown. */
  public GameMode resolveMode(RoomState room) {
    GameMode mode = gameModeRegistry.resolve(room == null ? null : room.rulesetId);
    return mode == null ? defaultGameMode : mode;
  }

  /** Effective {@link ModeRules} combining mode defaults with the room's {@code matchMode}. */
  public ModeRules resolveRules(RoomState room) {
    GameMode mode = resolveMode(room);
    if (mode instanceof DefaultGameMode dgm) {
      return dgm.rulesFor(room == null ? null : room.matchMode);
    }
    return mode.rules();
  }

  public int requiredPlayersToStart(RoomState room, GameRuntimeConfig cfg) {
    ModeRules rules = resolveRules(room);
    int min = rules.minPlayersToStart();
    return min > 0 ? min : Math.max(2, cfg.getMinPlayers());
  }

  public boolean sameTeam(RoomState room, String uidA, String uidB) {
    PlayerState a = room.playersByUid.get(uidA);
    PlayerState b = room.playersByUid.get(uidB);
    if (a == null || b == null) return false;
    return resolveRules(room).isFriendlyFire(a.teamId, b.teamId);
  }

  /** Assigns team ids for teams_2v2 by join order (slots 0–1 → A, 2–3 → B). No-op for FFA. */
  public void configureTeamsForMatch(RoomState room) {
    for (PlayerState p : room.players) {
      p.teamId = null;
    }
    if (!RoomStateFactory.MODE_TEAMS_2V2.equals(room.matchMode)) {
      return;
    }
    if (room.players.size() != 4) {
      log.warn(
          "sok teams_2v2 requires 4 players room={} count={}; falling back to ffa",
          room.id,
          room.players.size());
      room.matchMode = RoomStateFactory.MODE_FFA;
      return;
    }
    room.players.get(0).teamId = "A";
    room.players.get(1).teamId = "A";
    room.players.get(2).teamId = "B";
    room.players.get(3).teamId = "B";
  }

  /** Highest-score living member of {@code teamId}, or fall back to slot 0. */
  public String topScorerOnTeam(RoomState room, String teamId) {
    String bestUid = null;
    int bestScore = Integer.MIN_VALUE;
    for (PlayerState p : room.players) {
      if (!p.isEliminated && teamId.equals(p.teamId) && p.score > bestScore) {
        bestScore = p.score;
        bestUid = p.uid;
      }
    }
    return bestUid != null ? bestUid : room.players.get(0).uid;
  }
}
