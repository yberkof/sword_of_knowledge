package com.sok.backend.realtime.match;

import com.sok.backend.domain.game.QuestionEngineService;
import com.sok.backend.domain.game.tiebreaker.MemoryTieBreakRules;
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
  /**
   * {@code numeric} — estimation tiebreak; {@code xo} — tic-tac-toe; {@code avoid_bombs} — bomb hunt;
   * {@code collection} — vote lobby; {@code rps} / {@code rhythm} / {@code memory} — collection sub-games.
   */
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

  // ---------- collection lobby ----------
  /** {@code pick} while voting; cleared once a sub-minigame starts. */
  public String collectionSubPhase;
  public String collectionAttackerPick;
  public String collectionDefenderPick;
  public Long collectionPickDeadlineAtMs;

  // ---------- rock-paper-scissors (best of 3) ----------
  /** Wins toward 2 (BO3). */
  public int rpsAttackerWins;
  public int rpsDefenderWins;
  /** Pending throws for current round (rock/paper/scissors). */
  public String rpsPendingAttacker;
  public String rpsPendingDefender;

  // ---------- rhythm (Simon) ----------
  /** Round index; sequence length = 4 + rhythmRound. */
  public int rhythmRound;
  public int[] rhythmSequence;
  public Long rhythmRoundDeadlineAtMs;
  /** Comma-separated color indices (0..3), or null until submitted. */
  public String rhythmPendingAttackerInput;
  public String rhythmPendingDefenderInput;

  // ---------- memory 6×6 (18 pairs) ----------
  public String memorySubPhase;
  /** Length {@link MemoryTieBreakRules#GRID_CELLS}, values 0..17 pair id each exactly twice. */
  public int[] memoryPairByCell;
  /** Length GRID_CELLS, matched cells. */
  public boolean[] memoryMatched;
  /** Temporarily revealed for mismatch animation / turn — first selection index or -1. */
  public int memoryFirstPickIndex = -1;
  public String memoryTurnUid;
  public int memoryAttackerPairs;
  public int memoryDefenderPairs;
  public Long memoryPeekEndsAtMs;
}
