package com.sok.backend.realtime.socket;

/** Wire-level phase id strings (same as the legacy {@code SocketGateway} constants). */
public final class GamePhases {
  public static final String WAITING = "waiting";
  public static final String CASTLE_PLACEMENT = "castle_placement";
  public static final String CLAIMING_QUESTION = "claiming_question";
  public static final String CLAIMING_PICK = "claiming_pick";
  public static final String BATTLE = "battle";
  public static final String BATTLE_TIEBREAKER = "battle_tiebreaker";

  private GamePhases() {}
}

