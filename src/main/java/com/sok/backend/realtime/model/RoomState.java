package com.sok.backend.realtime.model;

import com.sok.backend.domain.game.NumericQuestion;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

public class RoomState {
  public String id, phase, hostUid, inviteCode, mapId, matchMode, rulesetId, claimTurnUid;
  public int round = 1, currentTurnIndex, roundAttackCount;
  public long matchStartedAt, lastActivityAt, phaseStartedAt;
  public Long phaseEndsAt;
  public List<PlayerState> players = new ArrayList<>();
  public Map<String, PlayerState> playersByUid = new HashMap<>();
  public Map<Integer, RegionState> regions = new HashMap<>();
  public Map<String, Integer> scoreByUid = new HashMap<>();
  public Map<String, Integer> claimPicksLeftByUid = new HashMap<>();
  public List<String> claimQueue = new ArrayList<>();
  public Map<String, AnswerMetric> estimationAnswers = new HashMap<>();
  public NumericQuestion activeNumericQuestion;
  public DuelState activeDuel;
}
