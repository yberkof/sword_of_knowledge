package com.sok.backend.realtime.model;

import com.sok.backend.domain.game.McqQuestion;
import com.sok.backend.domain.game.NumericQuestion;
import java.util.HashMap;
import java.util.Map;

public class DuelState {
  public String attackerUid, defenderUid, tiebreakKind;
  public int targetRegionId;
  public McqQuestion mcqQuestion;
  public NumericQuestion numericQuestion;
  public Map<String, DuelAnswer> answers = new HashMap<>();
  public Map<String, AnswerMetric> tiebreakerAnswers = new HashMap<>();
}
