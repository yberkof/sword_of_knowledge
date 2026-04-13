package com.sok.backend.domain.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TiebreakerRulesTest {
  private final TiebreakerRules rules = new TiebreakerRules();

  @Test
  void closestGuessBoundsMatchNode() {
    assertTrue(rules.validClosestGuess(0));
    assertTrue(rules.validClosestGuess(50_000_000));
    assertFalse(rules.validClosestGuess(50_000_001));
  }

  @Test
  void minefieldRequiresThreeDistinctCells() {
    assertTrue(rules.validMinefieldCells(Arrays.asList(1, 2, 3)));
    assertFalse(rules.validMinefieldCells(Arrays.asList(1, 1, 2)));
  }

  @Test
  void rhythmRequiresExpectedLength() {
    assertTrue(rules.validRhythmSequence(Arrays.asList(0, 1, 2), 3));
    assertFalse(rules.validRhythmSequence(Arrays.asList(0, 1, 2, 3), 3));
  }
}
