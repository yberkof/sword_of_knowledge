import { randomInt } from "node:crypto";
import { pickRandomClosestTieQuestion } from "./closestTieQuestions";
import {
  coerceRhythmNumberArray,
  coerceRhythmPads,
  normalizeRhythmPads,
} from "@sok/shared";

export const TIEBREAKER_GAME_IDS = ["minefield", "rhythm", "rps", "closest"] as const;
export type TiebreakerGameId = (typeof TIEBREAKER_GAME_IDS)[number];

export const TIEBREAKER_GAMES_UI: { id: TiebreakerGameId; title: string; blurb: string; emoji: string }[] = [
  { id: "minefield", title: "حقل الألغام", blurb: "ضع 3 ألغام لخصمك، ثم تناوبا على الخطوات — من يطأ 3 ألغام يخسر.", emoji: "💣" },
  { id: "rhythm", title: "إيقاع الذاكرة", blurb: "كرّر التسلسل — يبدأ سهلاً ويزداد طولاً حتى يخطئ أحدكما.", emoji: "🥁" },
  { id: "rps", title: "حجر ورقة مقص", blurb: "أفضل من 3 جولات.", emoji: "✊" },
  { id: "closest", title: "أقرب تخمين", blurb: "سؤال رقمي حقيقي — من يقترب أكثر من الإجابة الصحيحة؟", emoji: "🎯" },
];

function isValidGameId(id: string): id is TiebreakerGameId {
  return (TIEBREAKER_GAME_IDS as readonly string[]).includes(id);
}

export function randomTiebreakerGame(): TiebreakerGameId {
  return TIEBREAKER_GAME_IDS[randomInt(0, TIEBREAKER_GAME_IDS.length)]!;
}

function sortUniqueThree(cells: unknown[]): number[] | null {
  if (!Array.isArray(cells) || cells.length !== 3) return null;
  const nums = cells.map((x) => Math.trunc(Number(x)));
  if (nums.some((n) => !Number.isFinite(n))) return null;
  const s = [...new Set(nums)].sort((a, b) => a - b);
  if (s.length !== 3) return null;
  if (s[0]! < 0 || s[2]! > 8) return null;
  return s;
}

/** a wins iff (a-b) mod 3 === 1 in {rock,paper,scissors} cycle */
function rpsBeats(a: number, b: number): "a" | "b" | "tie" {
  if (a === b) return "tie";
  if ((a - b + 3) % 3 === 1) return "a";
  if ((b - a + 3) % 3 === 1) return "b";
  return "tie";
}

/** Each step is a pad index in 0..3 (four colors). Node `randomInt(min,max)` is max-exclusive → [0,4) → 0..3. */
function genRhythmPattern(round: number): number[] {
  const len = Math.min(3 + (round - 1), 12);
  const out: number[] = [];
  for (let i = 0; i < len; i++) {
    const v = randomInt(0, 4);
    out.push(((v % 4) + 4) % 4);
  }
  return out;
}

export type TieBreakerCtx = {
  attackerUid: string;
  defenderUid: string;
  targetHexId: number;
};

/** Attach to room; caller sets phase + clears activeDuel + timers */
export function openTiebreakerVote(room: { tieBreaker?: unknown }, ctx: TieBreakerCtx) {
  room.tieBreaker = {
    ...ctx,
    step: "vote",
    votes: {} as Record<string, TiebreakerGameId>,
  };
}

export function tiebreakerClientPayload(tb: Record<string, unknown> | undefined | null): Record<string, unknown> | null {
  if (!tb || typeof tb !== "object") return null;
  const step = String(tb.step || "");
  const aid = String(tb.attackerUid ?? "");
  const did = String(tb.defenderUid ?? "");
  const base: Record<string, unknown> = {
    attackerUid: tb.attackerUid,
    defenderUid: tb.defenderUid,
    targetHexId: tb.targetHexId,
    step,
  };
  if (tb.votes && typeof tb.votes === "object") base.votes = tb.votes;
  if (tb.selectedGame) base.selectedGame = tb.selectedGame;
  if (typeof tb.pickAgreed === "boolean") base.pickAgreed = tb.pickAgreed;
  if (step === "minefield_place") {
    base.placedBy = tb.placedBy || {};
  }
  if (step === "minefield_play") {
    base.turn = tb.mineTurn;
    base.revealedAttacker = tb.revealedAttacker || [];
    base.revealedDefender = tb.revealedDefender || [];
    base.hitCellsAttacker = tb.hitCellsAttacker || [];
    base.hitCellsDefender = tb.hitCellsDefender || [];
    base.attackerHits = tb.attackerHits ?? 0;
    base.defenderHits = tb.defenderHits ?? 0;
  }
  if (step === "rhythm") {
    base.rhythmRound = tb.rhythmRound;
    base.rhythmPattern = coerceRhythmPads(tb.rhythmPattern);
    const subs = (tb.rhythmSubmitted || {}) as Record<string, number[] | null | undefined>;
    base.rhythmReady = {
      [aid]: Array.isArray(subs[aid]),
      [did]: Array.isArray(subs[did]),
    };
  }
  if (step === "rps") {
    base.rpsRound = tb.rpsRound;
    base.rpsScores = tb.rpsScores || { attacker: 0, defender: 0 };
    base.rpsLast = tb.rpsLast ?? null;
    const pend = (tb.rpsPending || {}) as Record<string, number | undefined>;
    base.rpsReady = {
      [aid]: pend[aid] !== undefined,
      [did]: pend[did] !== undefined,
    };
  }
  if (step === "closest") {
    const subs = (tb.closestSubmitted || {}) as Record<string, number | undefined>;
    base.closestReady = {
      [aid]: subs[aid] !== undefined,
      [did]: subs[did] !== undefined,
    };
    const cq = tb.closestQuestionPublic;
    if (cq && typeof cq === "object") {
      base.closestQuestion = { id: (cq as { id?: string }).id, text: (cq as { text?: string }).text };
    }
  }
  return base;
}

export type VoteResult =
  | { kind: "wait" }
  | {
      kind: "picked";
      agreed: boolean;
      votes: Record<string, TiebreakerGameId>;
      selected: TiebreakerGameId;
    };

export function recordTiebreakerVote(
  room: { tieBreaker?: Record<string, unknown> },
  uid: string,
  gameIdRaw: string
): VoteResult | { kind: "error"; message: string } {
  const tb = room.tieBreaker;
  if (!tb || tb.step !== "vote") return { kind: "error", message: "no_vote_phase" };
  const gameId = String(gameIdRaw || "");
  if (!isValidGameId(gameId)) return { kind: "error", message: "bad_game" };
  const aid = String(tb.attackerUid);
  const did = String(tb.defenderUid);
  if (uid !== aid && uid !== did) return { kind: "error", message: "not_participant" };
  const votes = { ...(tb.votes as Record<string, TiebreakerGameId>) };
  if (votes[uid]) return { kind: "error", message: "already_voted" };
  votes[uid] = gameId;
  tb.votes = votes;
  const va = votes[aid];
  const vd = votes[did];
  if (!va || !vd) return { kind: "wait" };
  const agreed = va === vd;
  const selected = agreed ? va : randomTiebreakerGame();
  tb.selectedGame = selected;
  tb.pickAgreed = agreed;
  tb.step = "pick_resolved";
  return { kind: "picked", agreed, votes: { ...votes }, selected };
}

/** After pick animation delay — initialize the chosen minigame */
export function startTiebreakerGame(room: { tieBreaker?: Record<string, unknown> }): TiebreakerGameId | null {
  const tb = room.tieBreaker;
  if (!tb || tb.step !== "pick_resolved") return null;
  const g = tb.selectedGame as TiebreakerGameId | undefined;
  if (!g || !isValidGameId(g)) return null;

  if (g === "minefield") {
    tb.step = "minefield_place";
    tb.placedBy = { [String(tb.attackerUid)]: false, [String(tb.defenderUid)]: false };
    tb.bombsOnAttackerGrid = null;
    tb.bombsOnDefenderGrid = null;
    tb.mineTurn = "attacker";
    tb.revealedAttacker = [];
    tb.revealedDefender = [];
    tb.hitCellsAttacker = [];
    tb.hitCellsDefender = [];
    tb.attackerHits = 0;
    tb.defenderHits = 0;
    return g;
  }
  if (g === "rhythm") {
    tb.step = "rhythm";
    tb.rhythmRound = 1;
    tb.rhythmPattern = genRhythmPattern(1);
    tb.rhythmSubmitted = {};
    return g;
  }
  if (g === "rps") {
    tb.step = "rps";
    tb.rpsRound = 1;
    tb.rpsScores = { attacker: 0, defender: 0 };
    tb.rpsPending = {};
    tb.rpsLast = null;
    return g;
  }
  if (g === "closest") {
    const q = pickRandomClosestTieQuestion();
    tb.step = "closest";
    tb.closestTarget = q.answer;
    tb.closestQuestionPublic = { id: q.id, text: q.text };
    tb.closestSubmitted = {};
    return g;
  }
  return null;
}

export type MinePlaceResult =
  | { kind: "wait" }
  | { kind: "play_start" }
  | { kind: "error"; message: string };

export function recordMinefieldPlacement(
  room: { tieBreaker?: Record<string, unknown> },
  uid: string,
  cellsRaw: unknown
): MinePlaceResult {
  const tb = room.tieBreaker;
  if (!tb || tb.step !== "minefield_place") return { kind: "error", message: "bad_phase" };
  const aid = String(tb.attackerUid);
  const did = String(tb.defenderUid);
  if (uid !== aid && uid !== did) return { kind: "error", message: "not_participant" };
  const cells = sortUniqueThree(Array.isArray(cellsRaw) ? cellsRaw : []);
  if (!cells) return { kind: "error", message: "bad_cells" };

  if (uid === aid) {
    tb.bombsOnDefenderGrid = cells;
  } else {
    tb.bombsOnAttackerGrid = cells;
  }
  const pb = { ...(tb.placedBy as Record<string, boolean>) };
  pb[uid] = true;
  tb.placedBy = pb;

  const ba = tb.bombsOnAttackerGrid as number[] | null;
  const bd = tb.bombsOnDefenderGrid as number[] | null;
  if (!ba || !bd) return { kind: "wait" };

  tb.step = "minefield_play";
  tb.mineTurn = "attacker";
  return { kind: "play_start" };
}

export type MineStepResult =
  | { kind: "ok" }
  | { kind: "duel_done"; attackerWins: boolean }
  | { kind: "error"; message: string };

export function recordMinefieldStep(room: { tieBreaker?: Record<string, unknown> }, uid: string, cellRaw: unknown): MineStepResult {
  const tb = room.tieBreaker;
  if (!tb || tb.step !== "minefield_play") return { kind: "error", message: "bad_phase" };
  const aid = String(tb.attackerUid);
  const did = String(tb.defenderUid);
  const turn = String(tb.mineTurn);
  if (turn === "attacker" && uid !== aid) return { kind: "error", message: "not_your_turn" };
  if (turn === "defender" && uid !== did) return { kind: "error", message: "not_your_turn" };

  const cell = Math.trunc(Number(cellRaw));
  if (!Number.isFinite(cell) || cell < 0 || cell > 8) return { kind: "error", message: "bad_cell" };

  const bombsOnA = new Set(tb.bombsOnAttackerGrid as number[]);
  const bombsOnD = new Set(tb.bombsOnDefenderGrid as number[]);
  const revA = [...(tb.revealedAttacker as number[])];
  const revD = [...(tb.revealedDefender as number[])];

  const hitA = [...((tb.hitCellsAttacker as number[]) || [])];
  const hitD = [...((tb.hitCellsDefender as number[]) || [])];

  if (turn === "attacker") {
    if (revA.includes(cell)) return { kind: "error", message: "already_revealed" };
    revA.push(cell);
    tb.revealedAttacker = revA;
    if (bombsOnA.has(cell)) {
      hitA.push(cell);
      tb.hitCellsAttacker = hitA;
      tb.attackerHits = Number(tb.attackerHits ?? 0) + 1;
    }
    if (Number(tb.attackerHits) >= 3) {
      return { kind: "duel_done", attackerWins: false };
    }
    tb.mineTurn = "defender";
  } else {
    if (revD.includes(cell)) return { kind: "error", message: "already_revealed" };
    revD.push(cell);
    tb.revealedDefender = revD;
    if (bombsOnD.has(cell)) {
      hitD.push(cell);
      tb.hitCellsDefender = hitD;
      tb.defenderHits = Number(tb.defenderHits ?? 0) + 1;
    }
    if (Number(tb.defenderHits) >= 3) {
      return { kind: "duel_done", attackerWins: true };
    }
    tb.mineTurn = "attacker";
  }
  return { kind: "ok" };
}

export type RhythmResult =
  | { kind: "wait" }
  | { kind: "next_round"; pattern: number[] }
  | { kind: "duel_done"; attackerWins: boolean }
  | { kind: "error"; message: string };

export function recordRhythmSubmit(
  room: { tieBreaker?: Record<string, unknown> },
  uid: string,
  seqRaw: unknown
): RhythmResult {
  const tb = room.tieBreaker;
  if (!tb || tb.step !== "rhythm") return { kind: "error", message: "bad_phase" };
  const aid = String(tb.attackerUid);
  const did = String(tb.defenderUid);
  if (uid !== aid && uid !== did) return { kind: "error", message: "not_participant" };

  const targetRaw = coerceRhythmNumberArray(tb.rhythmPattern);
  if (!targetRaw || targetRaw.length === 0) return { kind: "error", message: "no_pattern" };
  const target = normalizeRhythmPads(targetRaw);
  tb.rhythmPattern = target;

  const subs = { ...(tb.rhythmSubmitted as Record<string, number[] | null>) };
  if (Array.isArray(subs[uid])) return { kind: "error", message: "already_submitted" };

  const seq = coerceRhythmNumberArray(seqRaw);
  if (!seq) return { kind: "error", message: "bad_seq" };
  /** Pads are exactly 0–3; do not mod-wrap (e.g. 4→0) or wrong taps could « match ». */
  if (seq.some((n) => !Number.isFinite(n) || n < 0 || n > 3 || Math.trunc(n) !== n)) {
    return { kind: "error", message: "bad_seq" };
  }
  if (seq.length !== target.length) return { kind: "error", message: "bad_seq_len" };

  subs[uid] = seq;
  tb.rhythmSubmitted = subs;

  const sa = subs[aid];
  const sd = subs[did];
  if (!Array.isArray(sa) || !Array.isArray(sd)) return { kind: "wait" };

  if (sa.length !== target.length || sd.length !== target.length) {
    return { kind: "error", message: "bad_seq_len" };
  }

  const matchA = sa.length === target.length && sa.every((v, i) => v === target[i]);
  const matchD = sd.length === target.length && sd.every((v, i) => v === target[i]);

  if (matchA && matchD) {
    const nextRound = Number(tb.rhythmRound ?? 1) + 1;
    tb.rhythmRound = nextRound;
    tb.rhythmPattern = genRhythmPattern(nextRound);
    tb.rhythmSubmitted = {};
    return { kind: "next_round", pattern: tb.rhythmPattern as number[] };
  }
  if (matchA && !matchD) return { kind: "duel_done", attackerWins: true };
  if (!matchA && matchD) return { kind: "duel_done", attackerWins: false };
  tb.rhythmSubmitted = {};
  return { kind: "next_round", pattern: target };
}

export type RpsResult =
  | { kind: "wait" }
  | { kind: "round_done"; picks: Record<string, number>; roundWinner: "attacker" | "defender" | "tie" }
  | { kind: "duel_done"; attackerWins: boolean }
  | { kind: "error"; message: string };

export function recordRpsSubmit(room: { tieBreaker?: Record<string, unknown> }, uid: string, pickRaw: unknown): RpsResult {
  const tb = room.tieBreaker;
  if (!tb || tb.step !== "rps") return { kind: "error", message: "bad_phase" };
  const aid = String(tb.attackerUid);
  const did = String(tb.defenderUid);
  if (uid !== aid && uid !== did) return { kind: "error", message: "not_participant" };
  const pick = Math.trunc(Number(pickRaw));
  if (pick !== 0 && pick !== 1 && pick !== 2) return { kind: "error", message: "bad_pick" };

  const pending = { ...(tb.rpsPending as Record<string, number | undefined>) };
  if (pending[uid] !== undefined) return { kind: "error", message: "already_submitted" };
  pending[uid] = pick;
  tb.rpsPending = pending;

  const pa = pending[aid];
  const pd = pending[did];
  if (pa === undefined || pd === undefined) return { kind: "wait" };

  const w = rpsBeats(pa, pd);
  const scores = { ...(tb.rpsScores as { attacker: number; defender: number }) };
  let roundWinner: "attacker" | "defender" | "tie" = "tie";
  if (w === "a") {
    scores.attacker += 1;
    roundWinner = "attacker";
  } else if (w === "b") {
    scores.defender += 1;
    roundWinner = "defender";
  }
  tb.rpsScores = scores;
  tb.rpsLast = { attackerPick: pa, defenderPick: pd, roundWinner };
  tb.rpsPending = {};
  tb.rpsRound = Number(tb.rpsRound ?? 1) + 1;

  if (scores.attacker >= 2) return { kind: "duel_done", attackerWins: true };
  if (scores.defender >= 2) return { kind: "duel_done", attackerWins: false };

  return { kind: "round_done", picks: { [aid]: pa, [did]: pd }, roundWinner };
}

export type ClosestResult =
  | { kind: "wait" }
  | {
      kind: "duel_done";
      attackerWins: boolean;
      target: number;
      guesses: Record<string, number>;
      questionText?: string;
    }
  | { kind: "error"; message: string };

export function recordClosestSubmit(room: { tieBreaker?: Record<string, unknown> }, uid: string, valueRaw: unknown): ClosestResult {
  const tb = room.tieBreaker;
  if (!tb || tb.step !== "closest") return { kind: "error", message: "bad_phase" };
  const aid = String(tb.attackerUid);
  const did = String(tb.defenderUid);
  if (uid !== aid && uid !== did) return { kind: "error", message: "not_participant" };
  const target = Number(tb.closestTarget);
  if (!Number.isFinite(target)) return { kind: "error", message: "no_target" };

  const value = Math.trunc(Number(valueRaw));
  if (!Number.isFinite(value) || value < -50_000_000 || value > 50_000_000) {
    return { kind: "error", message: "bad_value" };
  }

  const subs = { ...(tb.closestSubmitted as Record<string, number | undefined>) };
  if (subs[uid] !== undefined) return { kind: "error", message: "already_submitted" };
  subs[uid] = value;
  tb.closestSubmitted = subs;

  const ga = subs[aid];
  const gd = subs[did];
  if (ga === undefined || gd === undefined) return { kind: "wait" };

  const da = Math.abs(ga - target);
  const dd = Math.abs(gd - target);
  let attackerWins = false;
  if (da < dd) attackerWins = true;
  else if (dd < da) attackerWins = false;
  else attackerWins = randomInt(0, 2) === 0;

  const pub = tb.closestQuestionPublic as { text?: string } | undefined;
  return {
    kind: "duel_done",
    attackerWins,
    target,
    guesses: { [aid]: ga, [did]: gd },
    questionText: typeof pub?.text === "string" ? pub.text : undefined,
  };
}
