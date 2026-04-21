package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.service.config.GameRuntimeConfig;
import java.util.Arrays;
import org.springframework.stereotype.Service;

/**
 * Applies one X-O move and classifies the outcome. Does not perform I/O — gateway emits events / finishBattle.
 */
@Service
public class XoTieBreakInteractionService {

  public enum OutcomeType {
    INVALID_OCCUPIED,
    ATTACKER_WIN,
    DEFENDER_WIN,
    DRAW_REPLAY,
    DRAW_DEFENDER_WINS,
    CONTINUE
  }

  public record MoveOutcome(OutcomeType outcomeType, int replayNumber) {}

  public MoveOutcome applyMove(DuelState duel, String uid, int cellIndex, GameRuntimeConfig cfg) {
    if (duel.xoCells == null || cellIndex < 0 || cellIndex > 8 || duel.xoCells[cellIndex] != 0) {
      return new MoveOutcome(OutcomeType.INVALID_OCCUPIED, 0);
    }
    duel.xoCells[cellIndex] = uid.equals(duel.attackerUid) ? 1 : 2;
    int w = XoBoardRules.winner(duel.xoCells);
    if (w == 1) return new MoveOutcome(OutcomeType.ATTACKER_WIN, 0);
    if (w == 2) return new MoveOutcome(OutcomeType.DEFENDER_WIN, 0);
    if (XoBoardRules.boardFull(duel.xoCells)) {
      if (duel.xoReplayCount < cfg.getXoDrawMaxReplay()) {
        duel.xoReplayCount++;
        Arrays.fill(duel.xoCells, 0);
        duel.xoTurnUid = duel.attackerUid;
        return new MoveOutcome(OutcomeType.DRAW_REPLAY, duel.xoReplayCount);
      }
      return new MoveOutcome(OutcomeType.DRAW_DEFENDER_WINS, 0);
    }
    duel.xoTurnUid = uid.equals(duel.attackerUid) ? duel.defenderUid : duel.attackerUid;
    return new MoveOutcome(OutcomeType.CONTINUE, 0);
  }
}
