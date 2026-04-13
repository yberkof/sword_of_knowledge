package com.sok.backend.domain.game;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TiebreakerRules {
  private static final int CLOSEST_MIN = -50_000_000;
  private static final int CLOSEST_MAX = 50_000_000;

  public boolean validClosestGuess(Integer value) {
    return value != null && value >= CLOSEST_MIN && value <= CLOSEST_MAX;
  }

  public boolean validRpsPick(Integer pick) {
    return pick != null && pick >= 0 && pick <= 2;
  }

  public boolean validMinefieldCells(List<Integer> cells) {
    if (cells == null || cells.size() != 3) {
      return false;
    }
    Set<Integer> distinct = new HashSet<>(cells);
    return distinct.size() == 3;
  }

  public boolean validRhythmSequence(List<Integer> sequence, int expectedLength) {
    if (sequence == null || sequence.size() != expectedLength) {
      return false;
    }
    return sequence.stream().allMatch(p -> p != null && p >= 0 && p <= 3);
  }
}
