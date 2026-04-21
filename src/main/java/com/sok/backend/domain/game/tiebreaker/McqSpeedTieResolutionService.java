package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.service.config.GameRuntimeConfig;
import org.springframework.stereotype.Service;

/**
 * Pure policy for MCQ "speed ties" (both correct, same latency). {@link com.sok.backend.realtime.SocketGateway}
 * applies side effects.
 */
@Service
public class McqSpeedTieResolutionService {

  /**
   * @param currentMcqSpeedTieRetries retries already recorded for this attack chain
   */
  public McqSpeedTieOutcome resolve(GameRuntimeConfig cfg, int currentMcqSpeedTieRetries) {
    String mode = TieBreakerModeIds.normalize(cfg.getTieBreakerMode());
    if (TieBreakerModeIds.ATTACKER_ADVANTAGE.equals(mode)) {
      return McqSpeedTieOutcome.finish(true);
    }
    if (TieBreakerModeIds.MCQ_RETRY.equals(mode)) {
      int next = currentMcqSpeedTieRetries + 1;
      if (next <= cfg.getMaxMcqTieRetries()) {
        return McqSpeedTieOutcome.retryMcq(next);
      }
      return McqSpeedTieOutcome.enterTieAttack(true, TieBreakerModeIds.NUMERIC_CLOSEST);
    }
    return McqSpeedTieOutcome.enterTieAttack(false, null);
  }
}
