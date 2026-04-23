package com.sok.backend.domain.game.tiebreaker;

/** Constants for the collection memory minigame (6×6, 18 pairs). */
public final class MemoryTieBreakRules {

  private MemoryTieBreakRules() {}

  public static final int GRID_ROWS = 6;
  public static final int GRID_COLS = 6;
  public static final int GRID_CELLS = GRID_ROWS * GRID_COLS;
  /** Distinct pair types (each appears on exactly two cells). */
  public static final int PAIR_TYPES = 18;
}
