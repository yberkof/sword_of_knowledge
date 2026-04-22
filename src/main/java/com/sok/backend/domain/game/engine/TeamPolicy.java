package com.sok.backend.domain.game.engine;

/**
 * How players are grouped for the purposes of attack validation, start-condition sizing and win
 * resolution. Expressed as a policy field on {@link ModeRules} rather than as a separate
 * {@link GameMode} because the phase flow is otherwise identical.
 */
public enum TeamPolicy {
  /** Free-for-all. Every player is their own team. */
  NONE("ffa"),
  /** Two teams of two; join order 0,1 → A, 2,3 → B. Requires four players. */
  TEAMS_2V2("teams_2v2");

  private final String wireName;

  TeamPolicy(String wireName) {
    this.wireName = wireName;
  }

  public String wireName() {
    return wireName;
  }

  public static TeamPolicy fromWireName(String wireName) {
    if (wireName == null) return NONE;
    for (TeamPolicy p : values()) {
      if (p.wireName.equalsIgnoreCase(wireName.trim())) return p;
    }
    return NONE;
  }

  public int requiredPlayersToStart(int defaultMinPlayers) {
    return this == TEAMS_2V2 ? 4 : Math.max(2, defaultMinPlayers);
  }
}
