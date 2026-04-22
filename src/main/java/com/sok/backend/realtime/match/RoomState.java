package com.sok.backend.realtime.match;

import com.sok.backend.domain.game.QuestionEngineService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * Authoritative per-match state. Previously a private nested class on {@code SocketGateway}; now
 * lifted so the engine layer ({@code MatchContext}, {@link RoomSnapshot}) can reference it
 * directly. Behaviour is unchanged — field names and defaults match the original.
 */
public class RoomState {
  public static final String PHASE_WAITING = "waiting";
  public static final String PHASE_CASTLE = "castle_placement";
  public static final String PHASE_CLAIM_Q = "claiming_question";
  public static final String PHASE_CLAIM_PICK = "claiming_pick";
  public static final String PHASE_BATTLE = "battle";
  public static final String PHASE_DUEL = "duel";
  public static final String PHASE_TIE = "battle_tiebreaker";
  public static final String PHASE_ENDED = "ended";

  public String id;
  /** Mirrors client map registry id (e.g. Marefa basic_1v1_map). */
  public String mapId;
  /** "ffa" or "teams_2v2". */
  public String matchMode;
  /** Stable id for alternate rules / game modes — resolves to a {@code GameMode} bean. */
  public String rulesetId;
  public List<PlayerState> players = new ArrayList<PlayerState>();
  public Map<String, PlayerState> playersByUid = new HashMap<String, PlayerState>();
  public Map<Integer, RegionState> regions = new HashMap<Integer, RegionState>();
  public String phase = PHASE_WAITING;
  public int currentTurnIndex = 0;
  public String hostUid;
  public String inviteCode;
  public int round = 1;
  public int roundAttackCount = 0;
  public DuelState activeDuel;
  public long createdAt;
  public long lastActivityAt;
  public long matchStartedAt;
  public long phaseStartedAt;
  public Map<String, Integer> scoreByUid = new HashMap<String, Integer>();
  public Map<String, Integer> claimPicksLeftByUid = new HashMap<String, Integer>();
  public List<String> claimQueue = new ArrayList<String>();
  public String claimTurnUid;
  public Map<String, AnswerMetric> estimationAnswers = new HashMap<String, AnswerMetric>();
  public QuestionEngineService.NumericQuestion activeNumericQuestion;
  public Map<String, ScheduledFuture<?>> timers = new HashMap<String, ScheduledFuture<?>>();
  /** MCQ same-latency tie: number of extra MCQ rounds already played (mcq_retry mode). */
  public int mcqSpeedTieRetries;
  /** When non-null, forces the next tie-break resolution path (e.g. numeric after mcq_retry exhaustion). */
  public String tieBreakOverride;
}
