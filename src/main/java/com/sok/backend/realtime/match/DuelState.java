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
  /** {@code numeric} — estimation tiebreak; {@code xo} — tic-tac-toe minigame. */
  public String tiebreakKind;
  public int[] xoCells;
  public String xoTurnUid;
  public int xoReplayCount;
}
