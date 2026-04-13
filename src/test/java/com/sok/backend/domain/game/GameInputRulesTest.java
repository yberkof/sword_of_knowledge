package com.sok.backend.domain.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GameInputRulesTest {
  private final GameInputRules rules = new GameInputRules();

  @Test
  void normalizeInviteMatchesNodeRules() {
    assertEquals("ABCD12", rules.normalizePrivateCode("ab-cd_12"));
    assertEquals("ABCDEFGH", rules.normalizePrivateCode("abcdefghi"));
  }

  @Test
  void sanitizeChatBlocksProfanity() {
    assertEquals("", rules.sanitizeChatMessage("hello spam"));
    assertEquals("hello", rules.sanitizeChatMessage(" hello "));
  }

  @Test
  void coerceChoiceIndexHandlesNumberAndString() {
    assertEquals(4, rules.coerceChoiceIndex(4.8));
    assertEquals(7, rules.coerceChoiceIndex("7"));
    assertNull(rules.coerceChoiceIndex("x"));
  }
}
