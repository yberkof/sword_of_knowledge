package com.sok.backend.realtime.match;

import com.sok.backend.domain.game.QuestionEngineService;
import java.util.HashMap;
import java.util.Map;

/**
 * Authoritative duel snapshot held on the room while an MCQ duel or tie-break sub-phase runs.
 */
public class DuelState {
  public String attackerUid;
  public String defenderUid;
  public int targetRegionId;
  public QuestionEngineService.McqQuestion mcqQuestion;
  public QuestionEngineService.NumericQuestion numericQuestion;
  public Map<String, DuelAnswer> answers = new HashMap<String, DuelAnswer>();
  public Map<String, AnswerMetric> tiebreakerAnswers = new HashMap<String, AnswerMetric>();
  /** {@code numeric} — estimation tiebreak; {@code xo} — tic-tac-toe minigame; {@code avoid_bombs} — 3×3 bomb hunt. */
  public String tiebreakKind;
  public int[] xoCells;
  public String xoTurnUid;
  public int xoReplayCount;

  // ---------- avoid_bombs minigame state ----------
  /** {@code placement} while both players are hiding bombs, {@code opening} during reveal, {@code null} when inactive. */
  public String avoidBombsSubPhase;
  /** Per-uid 9-length board: 0 = safe, 1 = bomb. Server-only (never broadcast). */
  public Map<String, int[]> avoidBombsBoards = new HashMap<String, int[]>();
  /** Per-uid 9-length mask of cells the opponent opened in this uid's board. Public. */
  public Map<String, int[]> avoidBombsOpened = new HashMap<String, int[]>();
  /** Per-uid boolean: has this uid committed their 3 bomb positions? */
  public Map<String, Boolean> avoidBombsPlaced = new HashMap<String, Boolean>();
  /** Per-uid count of bombs this uid has opened on the opponent's board. 3 bombs ⇒ they lose. */
  public Map<String, Integer> avoidBombsHitsBy = new HashMap<String, Integer>();
  /** Uid whose turn it is during the opening sub-phase. Attacker always opens first. */
  public String avoidBombsTurnUid;
}
