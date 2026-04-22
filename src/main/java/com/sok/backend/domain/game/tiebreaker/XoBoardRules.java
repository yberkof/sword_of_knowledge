package com.sok.backend.domain.game.tiebreaker;

import java.util.Arrays;

/**
 * Pure rules for 3×3 tic-tac-toe used in tie-break. Cell values: 0 empty, 1 attacker, 2 defender.
 */
public final class XoBoardRules {

  private XoBoardRules() {}

  private static final int[][] LINES =
      new int[][] {
        {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
        {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
        {0, 4, 8}, {2, 4, 6}
      };

  /** @return 0 none, 1 attacker line, 2 defender line */
  public static int winner(int[] cells) {
    if (cells == null || cells.length != 9) return 0;
    for (int[] ln : LINES) {
      int a = cells[ln[0]];
      if (a == 0) continue;
      if (cells[ln[1]] == a && cells[ln[2]] == a) return a;
    }
    return 0;
  }

  public static boolean boardFull(int[] cells) {
    if (cells == null) return true;
    for (int c : cells) {
      if (c == 0) return false;
    }
    return true;
  }

  public static int[] emptyBoard() {
    return new int[9];
  }

  public static void clear(int[] cells) {
    Arrays.fill(cells, 0);
  }
}
