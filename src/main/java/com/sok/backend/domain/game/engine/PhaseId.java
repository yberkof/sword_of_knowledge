package com.sok.backend.domain.game.engine;

/**
 * Enumerated successor to the string phase constants on {@code SocketGateway}. The wire name
 * returned by {@link #wireName()} is what clients observe on {@code room_update.phase}.
 */
public enum PhaseId {
  WAITING("waiting"),
  CASTLE_PLACEMENT("castle_placement"),
  CLAIMING_QUESTION("claiming_question"),
  CLAIMING_PICK("claiming_pick"),
  BATTLE("battle"),
  DUEL("duel"),
  BATTLE_TIEBREAKER("battle_tiebreaker"),
  ENDED("ended");

  private final String wireName;

  PhaseId(String wireName) {
    this.wireName = wireName;
  }

  public String wireName() {
    return wireName;
  }

  public static PhaseId fromWireName(String wireName) {
    if (wireName == null) return WAITING;
    for (PhaseId id : values()) {
      if (id.wireName.equals(wireName)) return id;
    }
    return WAITING;
  }
}
