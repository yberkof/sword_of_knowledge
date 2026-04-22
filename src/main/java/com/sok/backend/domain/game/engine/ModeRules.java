package com.sok.backend.domain.game.engine;

/**
 * Immutable rule set used by {@link GameMode} implementations to override the default
 * {@code GameRuntimeConfig} values on a per-mode basis. Phases and {@code SocketGateway} branches
 * read these instead of hardcoded constants.
 *
 * <p>Treat this as "the knobs a mode can turn without rewriting phases". When a mode needs to
 * change *how* a phase works (not just its numbers), it ships its own {@link Phase} bean instead.
 */
public record ModeRules(
    TeamPolicy teamPolicy,
    int minPlayersToStart,
    int maxPlayers,
    int maxRounds,
    int initialCastleHp,
    int claimFirstPicks,
    int claimSecondPicks,
    int duelDurationMs,
    int claimDurationMs,
    int tiebreakDurationMs,
    String tieBreakerMode,
    int maxMcqTieRetries,
    int xoDrawMaxReplay) {

  public ModeRules {
    if (teamPolicy == null) teamPolicy = TeamPolicy.NONE;
    if (tieBreakerMode == null || tieBreakerMode.isBlank()) tieBreakerMode = "numeric_closest";
    if (minPlayersToStart <= 0) minPlayersToStart = teamPolicy == TeamPolicy.TEAMS_2V2 ? 4 : 2;
    if (maxPlayers < minPlayersToStart) maxPlayers = minPlayersToStart;
    if (maxRounds <= 0) maxRounds = 30;
    if (initialCastleHp <= 0) initialCastleHp = 3;
    if (claimFirstPicks < 0) claimFirstPicks = 0;
    if (claimSecondPicks < 0) claimSecondPicks = 0;
    if (duelDurationMs <= 0) duelDurationMs = 10000;
    if (claimDurationMs <= 0) claimDurationMs = 18000;
    if (tiebreakDurationMs <= 0) tiebreakDurationMs = 12000;
    if (maxMcqTieRetries < 0) maxMcqTieRetries = 0;
    if (xoDrawMaxReplay < 0) xoDrawMaxReplay = 0;
  }

  /**
   * Returns {@code true} when an attacker and a candidate victim belong to the same team and
   * therefore friendly-fire must be blocked.
   */
  public boolean isFriendlyFire(String attackerTeamId, String defenderTeamId) {
    if (teamPolicy == TeamPolicy.NONE) return false;
    if (attackerTeamId == null || defenderTeamId == null) return false;
    return attackerTeamId.equals(defenderTeamId);
  }
}
