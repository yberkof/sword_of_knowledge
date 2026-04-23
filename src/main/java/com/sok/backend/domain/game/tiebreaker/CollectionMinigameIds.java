package com.sok.backend.domain.game.tiebreaker;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Wire / config ids for collection tie-break voting and active sub-games. */
public final class CollectionMinigameIds {

  private CollectionMinigameIds() {}

  public static final String AVOID_BOMBS = "avoid_bombs";
  public static final String RPS = "rps";
  public static final String RHYTHM = "rhythm";
  public static final String MEMORY = "memory";

  public static final List<String> VOTE_OPTIONS =
      Collections.unmodifiableList(
          Arrays.asList(AVOID_BOMBS, RPS, RHYTHM, MEMORY));

  public static boolean isValidChoice(String s) {
    if (s == null) return false;
    return AVOID_BOMBS.equals(s) || RPS.equals(s) || RHYTHM.equals(s) || MEMORY.equals(s);
  }
}
