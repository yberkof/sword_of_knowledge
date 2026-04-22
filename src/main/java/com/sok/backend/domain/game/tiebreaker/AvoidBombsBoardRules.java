package com.sok.backend.domain.game.tiebreaker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Pure rules for the avoid-bombs tie-break minigame. Two 3×3 boards (one per player). Each player
 * hides {@link #BOMB_COUNT} bombs in their own grid during the placement window; then players
 * alternate opening cells on their opponent's grid. The first player to open
 * {@link #BOMB_COUNT} bombs loses.
 *
 * <p>Board cell values for the hidden layout: {@code 0} = safe, {@code 1} = bomb.
 * Opened-mask cell values: {@code 0} = not opened, {@code 1} = opened.
 */
public final class AvoidBombsBoardRules {

  private AvoidBombsBoardRules() {}

  public static final int GRID_SIZE = 9;
  public static final int BOMB_COUNT = 3;
  /** Reaching this many bombs opened (by a single opener) ends the duel — that opener loses. */
  public static final int LOSE_THRESHOLD = BOMB_COUNT;

  public static int[] emptyBoard() {
    return new int[GRID_SIZE];
  }

  /** Returns a fresh validated bomb layout, or {@code null} if the input is illegal. */
  public static int[] layoutFrom(Collection<Integer> cells) {
    if (cells == null || cells.size() != BOMB_COUNT) return null;
    HashSet<Integer> unique = new HashSet<Integer>();
    int[] board = emptyBoard();
    for (Integer c : cells) {
      if (c == null || c < 0 || c >= GRID_SIZE) return null;
      if (!unique.add(c)) return null;
      board[c] = 1;
    }
    return board;
  }

  /** Randomly fills a board with {@link #BOMB_COUNT} bombs. Used when placement timer expires. */
  public static int[] randomLayout(Random rnd) {
    int[] board = emptyBoard();
    int placed = 0;
    while (placed < BOMB_COUNT) {
      int idx = rnd.nextInt(GRID_SIZE);
      if (board[idx] == 0) {
        board[idx] = 1;
        placed++;
      }
    }
    return board;
  }

  public static boolean cellAlreadyOpened(int[] openedMask, int cellIndex) {
    if (openedMask == null || cellIndex < 0 || cellIndex >= openedMask.length) return true;
    return openedMask[cellIndex] != 0;
  }

  public static boolean isBomb(int[] board, int cellIndex) {
    if (board == null || cellIndex < 0 || cellIndex >= board.length) return false;
    return board[cellIndex] == 1;
  }

  public static void markOpened(int[] openedMask, int cellIndex) {
    if (openedMask == null || cellIndex < 0 || cellIndex >= openedMask.length) return;
    openedMask[cellIndex] = 1;
  }

  public static boolean loseReached(int hitsByOpener) {
    return hitsByOpener >= LOSE_THRESHOLD;
  }

  /** Cell indices where the opened-mask is marked (same wire shape as {@link #bombIndices} lists). */
  public static List<Integer> openedCellIndices(int[] openedMask) {
    ArrayList<Integer> out = new ArrayList<>();
    if (openedMask == null) {
      return out;
    }
    for (int i = 0; i < openedMask.length; i++) {
      if (openedMask[i] != 0) {
        out.add(i);
      }
    }
    return out;
  }

  /** For post-duel reveal: returns the indices where bombs sit on a board. */
  public static int[] bombIndices(int[] board) {
    if (board == null) return new int[0];
    int[] tmp = new int[BOMB_COUNT];
    int n = 0;
    for (int i = 0; i < board.length && n < tmp.length; i++) {
      if (board[i] == 1) tmp[n++] = i;
    }
    return Arrays.copyOf(tmp, n);
  }
}
