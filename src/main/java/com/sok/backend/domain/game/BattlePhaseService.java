package com.sok.backend.domain.game;

import org.springframework.stereotype.Service;

@Service
public class BattlePhaseService {
  public enum DuelOutcome {
    ATTACKER_WINS,
    DEFENDER_WINS,
    TIE
  }

  public DuelOutcome resolveMcq(
      boolean attackerCorrect,
      boolean defenderCorrect,
      long attackerLatencyMs,
      long defenderLatencyMs,
      boolean hasHumanDefender) {
    if (!hasHumanDefender) {
      return attackerCorrect ? DuelOutcome.ATTACKER_WINS : DuelOutcome.DEFENDER_WINS;
    }
    if (attackerCorrect && defenderCorrect && attackerLatencyMs == defenderLatencyMs) {
      return DuelOutcome.TIE;
    }
    if (attackerCorrect && !defenderCorrect) return DuelOutcome.ATTACKER_WINS;
    if (!attackerCorrect && defenderCorrect) return DuelOutcome.DEFENDER_WINS;
    if (!attackerCorrect && !defenderCorrect) return DuelOutcome.DEFENDER_WINS;
    return attackerLatencyMs <= defenderLatencyMs
        ? DuelOutcome.ATTACKER_WINS
        : DuelOutcome.DEFENDER_WINS;
  }
}
