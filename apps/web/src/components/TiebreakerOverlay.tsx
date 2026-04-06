import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import type { Socket } from 'socket.io-client';
import type { MatchState } from '../types';
import { coerceRhythmPads } from '@sok/shared';
import { TIEBREAKER_GAMES_UI, tiebreakerGameLabel, type TiebreakerGameId } from '../tieBreakerMeta';

/** Four fixed pads — indices 0–3 match server / queue chips. */
const RHYTHM_PADS = [
  {
    gradient: 'from-rose-500 via-rose-600 to-rose-900',
    border: 'border-rose-300/60',
    label: 'أحمر',
  },
  {
    gradient: 'from-amber-300 via-amber-500 to-amber-800',
    border: 'border-amber-200/70',
    label: 'عنبر',
  },
  {
    gradient: 'from-emerald-400 via-emerald-600 to-emerald-900',
    border: 'border-emerald-300/60',
    label: 'أخضر',
  },
  {
    gradient: 'from-sky-400 via-sky-600 to-sky-900',
    border: 'border-sky-300/60',
    label: 'أزرق',
  },
] as const;

const RPS_LABELS = ['حجر', 'ورقة', 'مقص'];
const RPS_EMOJI = ['✊', '✋', '✌️'];

/** How long the pattern queue stays visible (ms), scales with length */
function rhythmQueueHoldMs(len: number): number {
  return Math.min(7000, 1400 + len * 480);
}
/** Blank beat after queue hides before input + timer (ms) */
const RHYTHM_HIDE_GAP_MS = 600;
const RHYTHM_INPUT_BASE_S = 12;
const RHYTHM_INPUT_PER_STEP_S = 2;
const RHYTHM_INPUT_CAP_S = 50;

type RhythmUiPhase = 'queue' | 'gap' | 'input';

type Props = {
  socket: Socket;
  roomId: string;
  match: MatchState;
  myUid: string;
};

function playerName(match: MatchState, uid: string): string {
  return match.players.find((p) => p.uid === uid)?.name?.slice(0, 14) ?? 'لاعب';
}

function playerColor(match: MatchState, uid: string): string {
  return match.players.find((p) => p.uid === uid)?.color ?? '#d4af37';
}

export default function TiebreakerOverlay({ socket, roomId, match, myUid }: Props) {
  const tb = match.tieBreaker;
  const step = String(tb?.step ?? '');
  const aid = String(tb?.attackerUid ?? '');
  const did = String(tb?.defenderUid ?? '');
  const isAttacker = myUid === aid;
  const oppUid = isAttacker ? did : aid;
  const myName = playerName(match, myUid);
  const oppName = playerName(match, oppUid);
  const myCol = playerColor(match, myUid);
  const oppCol = playerColor(match, oppUid);

  const votes = (tb?.votes as Record<string, TiebreakerGameId> | undefined) ?? {};
  const myVote = votes[myUid];

  const [pickFlash, setPickFlash] = useState<{
    agreed: boolean;
    selected: string;
    votes: Record<string, string>;
  } | null>(null);

  const [rhythmReplayNonce, setRhythmReplayNonce] = useState(0);
  /** Authoritative pattern from server event until room state catches up (avoids stale tb on replay). */
  const [rhythmAwaitPattern, setRhythmAwaitPattern] = useState<number[] | null>(null);

  useEffect(() => {
    const onPick = (p: {
      agreed?: boolean;
      selected?: string;
      votes?: Record<string, string>;
    }) => {
      setPickFlash({
        agreed: Boolean(p.agreed),
        selected: String(p.selected || ''),
        votes: p.votes ?? {},
      });
    };
    const onRhythmNext = (p: { pattern?: unknown }) => {
      const next = coerceRhythmPads(p?.pattern);
      if (next.length > 0) setRhythmAwaitPattern(next);
      setRhythmReplayNonce((n) => n + 1);
    };
    socket.on('tiebreaker_pick_result', onPick);
    socket.on('tiebreaker_rhythm_next', onRhythmNext);
    return () => {
      socket.off('tiebreaker_pick_result', onPick);
      socket.off('tiebreaker_rhythm_next', onRhythmNext);
    };
  }, [socket]);

  useEffect(() => {
    if (step !== 'pick_resolved') {
      setPickFlash(null);
      return;
    }
    const sg = tb?.selectedGame;
    if (sg && !pickFlash) {
      setPickFlash({
        agreed: Boolean(tb?.pickAgreed),
        selected: String(sg),
        votes: (tb?.votes as Record<string, string>) ?? {},
      });
    }
  }, [step, tb?.selectedGame, tb?.pickAgreed, tb?.votes, pickFlash]);

  const emitVote = useCallback(
    (gameId: TiebreakerGameId) => {
      if (myVote) return;
      socket.emit('tiebreaker_vote', { roomId, uid: myUid, gameId });
    },
    [socket, roomId, myUid, myVote]
  );

  const votePhase = step === 'vote';
  const revealPhase = step === 'pick_resolved';

  const minePlaced = (tb?.placedBy as Record<string, boolean> | undefined) ?? {};
  const mineReady = Boolean(minePlaced[myUid]);

  const [minePick, setMinePick] = useState<number[]>([]);
  useEffect(() => {
    if (step !== 'minefield_place') setMinePick([]);
  }, [step]);

  const toggleMineCell = (i: number) => {
    if (mineReady) return;
    setMinePick((prev) => {
      if (prev.includes(i)) return prev.filter((x) => x !== i);
      if (prev.length >= 3) return prev;
      return [...prev, i].sort((a, b) => a - b);
    });
  };

  const submitMines = () => {
    if (minePick.length !== 3 || mineReady) return;
    socket.emit('tiebreaker_minefield_place', { roomId, uid: myUid, cells: minePick });
  };

  const mineTurn = String(tb?.turn ?? tb?.mineTurn ?? '');
  const myTurnMine =
    (isAttacker && mineTurn === 'attacker') || (!isAttacker && mineTurn === 'defender');
  const revA = (tb?.revealedAttacker as number[] | undefined) ?? [];
  const revD = (tb?.revealedDefender as number[] | undefined) ?? [];
  const hitsA = Number(tb?.attackerHits ?? 0);
  const hitsD = Number(tb?.defenderHits ?? 0);

  const revealMine = (cell: number) => {
    if (!myTurnMine) return;
    socket.emit('tiebreaker_minefield_step', { roomId, uid: myUid, cell });
  };

  const patternTb = coerceRhythmPads(tb?.rhythmPattern);
  const patternTbSig = patternTb.join(',');
  const awaitSig = rhythmAwaitPattern?.join(',') ?? '';
  const pattern =
    rhythmAwaitPattern && rhythmAwaitPattern.length > 0 ? rhythmAwaitPattern : patternTb;
  const patternSig = pattern.join(',');

  useEffect(() => {
    if (!rhythmAwaitPattern || rhythmAwaitPattern.length === 0) return;
    if (patternTbSig === awaitSig) setRhythmAwaitPattern(null);
  }, [patternTbSig, awaitSig, rhythmAwaitPattern]);

  const rhythmRound = Number(tb?.rhythmRound ?? 1);
  const rhythmReady = (tb?.rhythmReady as Record<string, boolean> | undefined) ?? {};
  const myRhythmDone = Boolean(rhythmReady[myUid]);

  const [echo, setEcho] = useState<number[]>([]);
  const echoRef = useRef<number[]>([]);
  const rhythmTimeoutSubmitRef = useRef(false);
  const [rhythmPhase, setRhythmPhase] = useState<RhythmUiPhase>('queue');
  const [rhythmInputUnlocked, setRhythmInputUnlocked] = useState(false);
  const [rhythmSecondsLeft, setRhythmSecondsLeft] = useState<number | null>(null);
  const [rhythmErr, setRhythmErr] = useState<string | null>(null);
  const [rpsShowFinale, setRpsShowFinale] = useState(false);

  useEffect(() => {
    let clearT: ReturnType<typeof setTimeout> | null = null;
    const onRhythmErr = (p: { code?: string }) => {
      if (clearT) clearTimeout(clearT);
      const m =
        p?.code === 'bad_seq_len'
          ? 'عدد الضغطات يجب أن يساوي طول الإيقاع تماماً.'
          : p?.code === 'bad_seq'
            ? 'تسلسل غير صالح (ألوان 0–3 فقط).'
            : 'تعذّر الإرسال — أعد المحاولة.';
      setRhythmErr(m);
      clearT = setTimeout(() => setRhythmErr(null), 4000);
    };
    socket.on('tiebreaker_rhythm_error', onRhythmErr);
    return () => {
      socket.off('tiebreaker_rhythm_error', onRhythmErr);
      if (clearT) clearTimeout(clearT);
    };
  }, [socket]);

  useEffect(() => {
    const onRpsRound = (p: { matchComplete?: boolean }) => {
      if (p?.matchComplete) setRpsShowFinale(true);
    };
    socket.on('tiebreaker_rps_round', onRpsRound);
    return () => socket.off('tiebreaker_rps_round', onRpsRound);
  }, [socket]);

  useEffect(() => {
    if (step !== 'rps') setRpsShowFinale(false);
  }, [step]);

  useEffect(() => {
    if (step !== 'rhythm') setRhythmAwaitPattern(null);
  }, [step]);

  useEffect(() => {
    echoRef.current = echo;
  }, [echo]);

  useEffect(() => {
    setEcho([]);
  }, [rhythmRound, patternSig, rhythmReplayNonce]);

  /** Memorize: show queue → hide → unlock pads + start countdown */
  useEffect(() => {
    if (step !== 'rhythm') {
      setRhythmInputUnlocked(false);
      setRhythmSecondsLeft(null);
      return;
    }
    if (pattern.length === 0) return;

    setRhythmPhase('queue');
    setRhythmInputUnlocked(false);
    setRhythmSecondsLeft(null);
    rhythmTimeoutSubmitRef.current = false;

    const holdMs = rhythmQueueHoldMs(pattern.length);
    const tHide = window.setTimeout(() => setRhythmPhase('gap'), holdMs);
    const tInput = window.setTimeout(() => {
      setRhythmPhase('input');
      setRhythmInputUnlocked(true);
      const sec = Math.min(
        RHYTHM_INPUT_CAP_S,
        RHYTHM_INPUT_BASE_S + pattern.length * RHYTHM_INPUT_PER_STEP_S
      );
      setRhythmSecondsLeft(sec);
    }, holdMs + RHYTHM_HIDE_GAP_MS);

    return () => {
      window.clearTimeout(tHide);
      window.clearTimeout(tInput);
    };
  }, [step, rhythmRound, patternSig, rhythmReplayNonce]);

  /** Tick countdown; at 0 auto-send current taps (partial → server rejects length) */
  useEffect(() => {
    if (step !== 'rhythm' || rhythmPhase !== 'input' || myRhythmDone) return;
    if (rhythmSecondsLeft === null) return;

    if (rhythmSecondsLeft <= 0) {
      if (!rhythmTimeoutSubmitRef.current) {
        rhythmTimeoutSubmitRef.current = true;
        socket.emit('tiebreaker_rhythm_submit', {
          roomId,
          uid: myUid,
          sequence: [...echoRef.current],
        });
      }
      return;
    }

    const id = window.setTimeout(() => {
      setRhythmSecondsLeft((s) => (s == null ? s : s - 1));
    }, 1000);
    return () => window.clearTimeout(id);
  }, [step, rhythmPhase, myRhythmDone, rhythmSecondsLeft, socket, roomId, myUid]);

  const canSubmitRhythm =
    rhythmInputUnlocked && !myRhythmDone && echo.length === pattern.length && pattern.length > 0;

  const submitRhythm = () => {
    if (!canSubmitRhythm) return;
    rhythmTimeoutSubmitRef.current = true;
    socket.emit('tiebreaker_rhythm_submit', { roomId, uid: myUid, sequence: echo });
  };

  const rpsScores = (tb?.rpsScores as { attacker: number; defender: number } | undefined) ?? {
    attacker: 0,
    defender: 0,
  };
  const myRpsScore = isAttacker ? rpsScores.attacker : rpsScores.defender;
  const oppRpsScore = isAttacker ? rpsScores.defender : rpsScores.attacker;
  const rpsReady = (tb?.rpsReady as Record<string, boolean> | undefined) ?? {};
  const myRpsLocked = Boolean(rpsReady[myUid]);
  const [rpsPick, setRpsPick] = useState<number | null>(null);

  const rpsLast = tb?.rpsLast as
    | { attackerPick?: number; defenderPick?: number; roundWinner?: string }
    | null
    | undefined;

  const submitRps = () => {
    if (rpsPick === null || myRpsLocked) return;
    socket.emit('tiebreaker_rps_submit', { roomId, uid: myUid, pick: rpsPick });
    setRpsPick(null);
  };

  const closestReady = (tb?.closestReady as Record<string, boolean> | undefined) ?? {};
  const myClosestDone = Boolean(closestReady[myUid]);
  const [closestVal, setClosestVal] = useState('');
  const submitClosest = () => {
    const trimmed = closestVal.trim();
    if (!trimmed || myClosestDone) return;
    const n = Number(trimmed);
    if (!Number.isFinite(n) || Math.abs(n) > 50_000_000) return;
    const v = Math.trunc(n);
    socket.emit('tiebreaker_closest_submit', { roomId, uid: myUid, value: v });
  };

  const placementTitle = isAttacker ? 'ضع 3 ألغاماً في شبكة المدافع' : 'ضع 3 ألغاماً في شبكة المهاجم';

  const hitCA = (tb?.hitCellsAttacker as number[] | undefined) ?? [];
  const hitCD = (tb?.hitCellsDefender as number[] | undefined) ?? [];
  const myRevealed = isAttacker ? revA : revD;
  const myHitCells = isAttacker ? hitCA : hitCD;
  const myHits = isAttacker ? hitsA : hitsD;
  const oppHits = isAttacker ? hitsD : hitsA;

  const turnPlayerName =
    mineTurn === 'attacker' ? playerName(match, aid) : mineTurn === 'defender' ? playerName(match, did) : '…';

  const selectedMeta = useMemo(
    () => TIEBREAKER_GAMES_UI.find((g) => g.id === pickFlash?.selected),
    [pickFlash?.selected]
  );

  const lastRoundLine = useMemo(() => {
    if (!rpsLast || rpsLast.attackerPick == null || rpsLast.defenderPick == null) return null;
    const ap = rpsLast.attackerPick;
    const dp = rpsLast.defenderPick;
    const aLabel = `${RPS_EMOJI[ap]!} ${RPS_LABELS[ap]}`;
    const dLabel = `${RPS_EMOJI[dp]!} ${RPS_LABELS[dp]}`;
    const rw = rpsLast.roundWinner;
    let resultAr = 'تعادل في الجولة';
    if (rw === 'attacker') resultAr = `نقطة للمهاجم (${playerName(match, aid)})`;
    else if (rw === 'defender') resultAr = `نقطة للمدافع (${playerName(match, did)})`;
    return { aLabel, dLabel, resultAr };
  }, [rpsLast, match, aid, did]);

  const shellClass =
    'tb-panel relative z-[2] mx-auto w-full max-w-[min(100%,420px)] sm:max-w-md rounded-2xl border-2 border-gold/55 bg-[var(--color-background)] p-4 shadow-[0_8px_40px_rgba(0,0,0,0.45)] sm:p-5 max-h-[min(88dvh,820px)] overflow-y-auto overscroll-contain';

  return (
    <div className="fixed inset-0 z-[210] flex items-end justify-center bg-[#120808]/92 px-2 pb-[max(0.5rem,env(safe-area-inset-bottom))] pt-4 backdrop-blur-md sm:items-center sm:p-4 sm:pb-4">
      <AnimatePresence>
        {revealPhase && pickFlash?.selected ? (
          <motion.div
            key="pick-reveal"
            className="pointer-events-none absolute inset-0 z-[3] flex flex-col items-center justify-center bg-[#120808]/85"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <div className="absolute inset-0 overflow-hidden">
              {[...Array(20)].map((_, i) => (
                <motion.span
                  key={i}
                  className="absolute h-2 w-2 rounded-full bg-gold shadow-[0_0_12px_#d4af37]"
                  initial={{ x: '50vw', y: '50vh', scale: 0, opacity: 0 }}
                  animate={{
                    x: `${15 + Math.random() * 70}vw`,
                    y: `${10 + Math.random() * 75}vh`,
                    scale: [0, 1.2, 1],
                    opacity: [0, 1, 0.9],
                  }}
                  transition={{ duration: 1.2, delay: i * 0.05, ease: 'easeOut' }}
                />
              ))}
            </div>
            <motion.div
              className="relative z-[1] mx-3 max-w-lg rounded-2xl border-2 border-gold/70 bg-[var(--color-background)] px-6 py-8 text-center shadow-[0_0_50px_rgba(212,175,55,0.4)] sm:px-10 sm:py-10"
              initial={{ scale: 0.65, rotate: -5 }}
              animate={{ scale: 1, rotate: 0 }}
              transition={{ type: 'spring', stiffness: 140, damping: 15 }}
            >
              <p className="mb-2 font-mono text-[10px] font-bold tracking-[0.3em] text-[var(--color-primary)]">
                {pickFlash.agreed ? 'اتفقتما' : 'اختيار عشوائي'}
              </p>
              <motion.div
                className="mx-auto mb-4 flex h-20 w-20 items-center justify-center rounded-2xl bg-gradient-to-br from-gold/40 to-[var(--color-primary)]/25 text-5xl sm:h-24 sm:w-24 sm:text-6xl"
                animate={{ boxShadow: ['0 0 16px #d4af37', '0 0 40px #d4af37', '0 0 16px #d4af37'] }}
                transition={{ duration: 1.8, repeat: Infinity }}
              >
                {selectedMeta?.emoji ?? '⚔'}
              </motion.div>
              <h2 className="font-amiri text-2xl font-black text-[var(--color-ink)] sm:text-3xl">
                {tiebreakerGameLabel(pickFlash.selected)}
              </h2>
              <p className="mt-2 font-mono text-xs leading-relaxed text-[var(--color-ink)]/65">
                {selectedMeta?.blurb}
              </p>
            </motion.div>
          </motion.div>
        ) : null}
      </AnimatePresence>

      {votePhase ? (
        <div className={shellClass}>
          <div className="mb-4 text-center">
            <p className="font-mono text-[10px] font-bold tracking-[0.28em] text-[var(--color-primary)]">
              مواجهة فاصلة
            </p>
            <h2 className="font-amiri mt-1 text-xl font-black text-[var(--color-ink)] sm:text-2xl">
              اختارا نوع اللعبة
            </h2>
            <p className="mt-1.5 text-sm leading-snug text-[var(--color-ink)]/70">
              اتفاق → تُلعب مباشرة. اختلاف → يختار النظام عشوائياً.
            </p>
          </div>
          <div className="grid grid-cols-1 gap-2.5 sm:gap-3">
            {TIEBREAKER_GAMES_UI.map((g) => (
              <button
                key={g.id}
                type="button"
                disabled={Boolean(myVote)}
                onClick={() => emitVote(g.id)}
                className={`flex min-h-[52px] touch-manipulation items-start gap-3 rounded-xl border-2 p-3.5 text-right transition-all active:scale-[0.98] sm:min-h-0 sm:p-4 ${
                  myVote === g.id
                    ? 'border-gold bg-[var(--color-primary)]/12 ring-2 ring-gold/80'
                    : 'border-gold/40 bg-[var(--color-surface)]/8 hover:border-gold'
                } ${myVote && myVote !== g.id ? 'opacity-45' : ''}`}
              >
                <span className="text-2xl sm:text-3xl">{g.emoji}</span>
                <div className="min-w-0 flex-1">
                  <h3 className="font-amiri text-base font-bold text-[var(--color-ink)]">{g.title}</h3>
                  <p className="mt-0.5 text-xs leading-relaxed text-[var(--color-ink)]/60">{g.blurb}</p>
                </div>
              </button>
            ))}
          </div>
          <p className="mt-4 text-center font-mono text-[10px] text-[var(--color-ink)]/55">
            {myVote ? 'بانتظار اختيار الخصم…' : 'اضغط لعبة واحدة'}
          </p>
        </div>
      ) : null}

      {step === 'minefield_place' ? (
        <div className={shellClass}>
          <h2 className="font-amiri text-center text-lg font-black text-[var(--color-primary)] sm:text-xl">
            {placementTitle}
          </h2>
          <p className="mt-1 text-center text-sm text-[var(--color-ink)]/70">3 مربعات بالضبط</p>
          <div className="mx-auto mt-4 grid w-max grid-cols-3 gap-2.5">
            {[0, 1, 2, 3, 4, 5, 6, 7, 8].map((i) => {
              const on = minePick.includes(i);
              return (
                <button
                  key={i}
                  type="button"
                  disabled={mineReady}
                  onClick={() => toggleMineCell(i)}
                  className={`flex min-h-[48px] min-w-[48px] items-center justify-center rounded-xl border-2 text-base font-black touch-manipulation sm:h-[52px] sm:w-[52px] ${
                    on
                      ? 'border-gold bg-[var(--color-primary)]/20 text-[var(--color-ink)]'
                      : 'border-gold/35 bg-[var(--color-surface)]/15 text-[var(--color-ink)]/50'
                  } ${mineReady ? 'cursor-not-allowed opacity-45' : ''}`}
                >
                  {on ? '💣' : i + 1}
                </button>
              );
            })}
          </div>
          <button
            type="button"
            disabled={minePick.length !== 3 || mineReady}
            onClick={submitMines}
            className="btn-crimson vintage-border mt-5 min-h-[48px] w-full rounded-xl py-3 text-base font-bold disabled:opacity-40"
          >
            {mineReady ? '✓ تم وضع الألغام' : 'تأكيد الألغام'}
          </button>
        </div>
      ) : null}

      {step === 'minefield_play' ? (
        <div className={shellClass}>
          <div className="rounded-xl border border-gold/40 bg-[var(--color-surface)]/12 px-3 py-2.5">
            <p className="text-center font-mono text-[10px] font-bold tracking-wider text-[var(--color-primary)]">
              دور اللعب
            </p>
            <p className="font-amiri text-center text-lg font-black text-[var(--color-ink)]">{turnPlayerName}</p>
            <p className="text-center text-xs text-[var(--color-ink)]/65">
              {myTurnMine ? 'اختر مربعاً على شبكتك' : 'انتظر تحرك الخصم'}
            </p>
          </div>

          <div className="mt-4 grid grid-cols-2 gap-3">
            <div
              className="rounded-xl border-2 p-2.5"
              style={{ borderColor: myCol, backgroundColor: `${myCol}18` }}
            >
              <p className="truncate text-center font-mono text-[9px] font-bold uppercase text-[var(--color-ink)]/60">
                أنت · {myHits}/3 لغم
              </p>
              <div className="mt-1.5 flex justify-center gap-1">
                {[0, 1, 2].map((i) => (
                  <div
                    key={i}
                    className={`h-2.5 flex-1 max-w-[36px] rounded-full ${i < myHits ? 'bg-red-500' : 'bg-[var(--color-ink)]/15'}`}
                  />
                ))}
              </div>
            </div>
            <div
              className="rounded-xl border-2 p-2.5"
              style={{ borderColor: oppCol, backgroundColor: `${oppCol}18` }}
            >
              <p className="truncate text-center font-mono text-[9px] font-bold uppercase text-[var(--color-ink)]/60">
                {oppName} · {oppHits}/3
              </p>
              <div className="mt-1.5 flex justify-center gap-1">
                {[0, 1, 2].map((i) => (
                  <div
                    key={i}
                    className={`h-2.5 flex-1 max-w-[36px] rounded-full ${i < oppHits ? 'bg-red-500' : 'bg-[var(--color-ink)]/15'}`}
                  />
                ))}
              </div>
            </div>
          </div>

          <p className="font-amiri mt-3 text-center text-sm font-bold text-[var(--color-ink)]">شبكتك</p>
          <div className="mx-auto mt-2 grid w-max grid-cols-3 gap-2">
            {[0, 1, 2, 3, 4, 5, 6, 7, 8].map((i) => {
              const revealed = myRevealed.includes(i);
              const boom = myHitCells.includes(i);
              return (
                <button
                  key={i}
                  type="button"
                  disabled={!myTurnMine || revealed}
                  onClick={() => revealMine(i)}
                  className={`flex min-h-[48px] min-w-[48px] items-center justify-center rounded-xl border-2 text-lg touch-manipulation sm:h-[52px] sm:w-[52px] ${
                    revealed
                      ? boom
                        ? 'border-red-500 bg-red-950/50 text-2xl'
                        : 'border-emerald-600 bg-emerald-900/25 text-emerald-100'
                      : 'border-gold/45 bg-[var(--color-surface)]/20 text-[var(--color-ink)]/40'
                  }`}
                >
                  {revealed ? (boom ? '💥' : '✓') : '·'}
                </button>
              );
            })}
          </div>
        </div>
      ) : null}

      {step === 'rhythm' ? (
        <div className={shellClass}>
          <h2 className="font-amiri text-center text-lg font-black text-[var(--color-primary)]">
            إيقاع الذاكرة
          </h2>
          <p className="text-center font-mono text-xs text-[var(--color-ink)]/55">جولة {rhythmRound}</p>

          <div className="mt-3 rounded-xl border border-gold/40 bg-[var(--color-surface)]/8 px-2 py-4">
            <p className="mb-2 text-center font-mono text-[10px] font-bold text-[var(--color-primary)]">
              {rhythmPhase === 'queue'
                ? 'احفظ التسلسل — الطابور يختفي تلقائياً'
                : rhythmPhase === 'gap'
                  ? '… يُخفى التسلسل — استعد'
                  : rhythmPhase === 'input'
                    ? 'أعد نفس الألوان بالترتيب على اللوحة أدناه'
                    : '…'}
            </p>

            <div className="relative min-h-[4.5rem]">
              <AnimatePresence mode="wait">
                {rhythmPhase === 'queue' && pattern.length > 0 ? (
                  <motion.div
                    key={`q-${patternSig}`}
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.94, filter: 'blur(8px)' }}
                    transition={{ duration: 0.38, ease: [0.22, 1, 0.36, 1] }}
                    dir="ltr"
                    className="flex flex-wrap items-end justify-center gap-2 px-1 py-1"
                  >
                    {pattern.map((c, i) => {
                      const pad = RHYTHM_PADS[c]!;
                      return (
                        <div key={`${patternSig}-${i}`} className="flex flex-col items-center gap-0.5">
                          <div
                            className={`h-10 w-10 rounded-xl border-2 bg-gradient-to-br shadow-md sm:h-11 sm:w-11 ${pad.border} ${pad.gradient}`}
                            title={pad.label}
                          />
                          <span className="font-mono text-[9px] font-bold tabular-nums text-[var(--color-ink)]/45">
                            {i + 1}
                          </span>
                        </div>
                      );
                    })}
                  </motion.div>
                ) : null}
              </AnimatePresence>

              {rhythmPhase === 'gap' ? (
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="flex min-h-[4.5rem] items-center justify-center py-2"
                >
                  <p className="text-center font-mono text-xs text-[var(--color-ink)]/40">● ● ●</p>
                </motion.div>
              ) : null}

              {rhythmPhase === 'input' && !myRhythmDone && rhythmSecondsLeft !== null ? (
                <div className="mb-3 flex flex-col items-center gap-1.5">
                  <div className="flex items-center justify-center gap-2 rounded-xl border-2 border-gold/50 bg-[var(--color-primary)]/10 px-4 py-2">
                    <span className="font-mono text-[10px] font-bold text-[var(--color-ink)]/60">الوقت</span>
                    <span
                      className={`font-mono text-2xl font-black tabular-nums ${
                        rhythmSecondsLeft <= 5 ? 'text-red-600' : 'text-[var(--color-primary)]'
                      }`}
                    >
                      {rhythmSecondsLeft}
                    </span>
                    <span className="font-mono text-xs text-[var(--color-ink)]/55">ث</span>
                  </div>
                  <p className="text-center text-[10px] text-[var(--color-ink)]/45">
                    عند الصفر يُرسَل ما أدخلته تلقائياً
                  </p>
                </div>
              ) : null}
            </div>

            <p className="mb-2 text-center text-[10px] text-[var(--color-ink)]/50">
              ترتيب المربعات أسفل يطابق ترتيب الأرقام في الطابور (ثابت اتجاهاً)
            </p>

            <div
              dir="ltr"
              className="mx-auto grid max-w-[280px] grid-cols-4 gap-2 sm:max-w-[320px] sm:gap-2.5"
            >
              {[0, 1, 2, 3].map((c) => {
                const pad = RHYTHM_PADS[c]!;
                const inputOk = rhythmPhase === 'input' && rhythmInputUnlocked && !myRhythmDone;
                const lockedPad = !inputOk;
                return (
                  <motion.button
                    key={c}
                    type="button"
                    disabled={!inputOk}
                    onClick={() => inputOk && setEcho((e) => [...e, c])}
                    className={`relative flex aspect-square min-h-[52px] min-w-0 flex-col items-center justify-center overflow-hidden rounded-2xl border-2 bg-gradient-to-br touch-manipulation ${pad.border} ${pad.gradient} ${
                      inputOk ? 'active:scale-95' : ''
                    } ${lockedPad ? 'pointer-events-none opacity-[0.35]' : 'opacity-100 shadow-md'}`}
                  >
                    <span className="relative z-[1] text-[10px] font-bold text-white/90 drop-shadow-md">
                      {pad.label}
                    </span>
                  </motion.button>
                );
              })}
            </div>
          </div>

          <p className="mt-3 text-center font-mono text-xs font-bold text-[var(--color-ink)]">
            تسلسلك: {echo.length} / {pattern.length || '…'}
          </p>

          {rhythmErr ? (
            <p className="mt-2 rounded-lg border border-red-400/60 bg-red-500/10 px-2 py-1.5 text-center text-xs font-bold text-red-800">
              {rhythmErr}
            </p>
          ) : null}

          <div className="mt-2 flex min-h-[2.25rem] flex-wrap justify-center gap-1.5">
            {echo.map((c, i) => (
              <motion.span
                key={`${i}-${c}`}
                initial={{ scale: 0.5, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                className={`inline-block h-9 w-9 rounded-lg border border-white/30 bg-gradient-to-br shadow-md ${RHYTHM_PADS[c]?.gradient ?? 'from-zinc-600 to-zinc-900'}`}
              />
            ))}
          </div>

          <div className="mt-4 flex gap-2">
            <button
              type="button"
              disabled={myRhythmDone || !rhythmInputUnlocked}
              onClick={() => setEcho((e) => e.slice(0, -1))}
              className="min-h-[48px] flex-1 rounded-xl border-2 border-gold/45 py-2 text-sm font-bold text-[var(--color-ink)]"
            >
              تراجع
            </button>
            <button
              type="button"
              disabled={!canSubmitRhythm}
              onClick={submitRhythm}
              className="btn-crimson vintage-border min-h-[48px] flex-[1.6] rounded-xl py-2 text-sm font-bold disabled:opacity-40"
            >
              {myRhythmDone ? 'تم الإرسال' : 'إرسال'}
            </button>
          </div>
        </div>
      ) : null}

      {step === 'rps' ? (
        <div className={shellClass}>
          <h2 className="font-amiri text-center text-lg font-black text-[var(--color-primary)]">
            حجر · ورقة · مقص
          </h2>
          <p className="text-center font-mono text-[10px] text-[var(--color-ink)]/55">أول من يصل إلى نقطتين</p>

          {rpsShowFinale ? (
            <motion.div
              className="mt-3 rounded-xl border-2 border-gold bg-[var(--color-primary)]/12 px-3 py-2.5 text-center"
              animate={{ scale: [1, 1.02, 1] }}
              transition={{ repeat: Infinity, duration: 1.2 }}
            >
              <p className="font-amiri text-sm font-black text-[var(--color-primary)]">
                الجولة الحاسمة — شاهد النتيجة أدناه، ثم يُحمَل الملعب
              </p>
            </motion.div>
          ) : null}

          <div className="mt-4 grid grid-cols-2 gap-3">
            <div
              className="rounded-xl border-2 p-3 text-center"
              style={{ borderColor: myCol, backgroundColor: `${myCol}14` }}
            >
              <p className="truncate text-xs font-bold text-[var(--color-ink)]">{myName}</p>
              <p className="font-amiri mt-1 text-4xl font-black tabular-nums text-[var(--color-primary)]">
                {myRpsScore}
              </p>
            </div>
            <div
              className="rounded-xl border-2 p-3 text-center"
              style={{ borderColor: oppCol, backgroundColor: `${oppCol}14` }}
            >
              <p className="truncate text-xs font-bold text-[var(--color-ink)]">{oppName}</p>
              <p className="font-amiri mt-1 text-4xl font-black tabular-nums text-[var(--color-ink)]/80">
                {oppRpsScore}
              </p>
            </div>
          </div>

          {lastRoundLine ? (
            <div className="mt-4 rounded-xl border border-gold/40 bg-[var(--color-primary)]/8 px-3 py-2.5">
              <p className="text-center font-mono text-[10px] font-bold text-[var(--color-primary)]">
                نتيجة الجولة السابقة
              </p>
              <div className="mt-2 flex items-center justify-between gap-2 text-sm">
                <div className="min-w-0 flex-1 text-center">
                  <p className="truncate text-[10px] text-[var(--color-ink)]/55">مهاجم</p>
                  <p className="font-bold text-[var(--color-ink)]">{lastRoundLine.aLabel}</p>
                </div>
                <span className="text-gold">⚔</span>
                <div className="min-w-0 flex-1 text-center">
                  <p className="truncate text-[10px] text-[var(--color-ink)]/55">مدافع</p>
                  <p className="font-bold text-[var(--color-ink)]">{lastRoundLine.dLabel}</p>
                </div>
              </div>
              <p className="mt-2 text-center text-xs font-bold text-[var(--color-ink)]">{lastRoundLine.resultAr}</p>
            </div>
          ) : (
            <p className="mt-3 text-center text-xs text-[var(--color-ink)]/55">ابدأ الجولة الأولى</p>
          )}

          <div className="mt-4 grid grid-cols-3 gap-2">
            {RPS_LABELS.map((label, pick) => (
              <button
                key={label}
                type="button"
                disabled={myRpsLocked}
                onClick={() => setRpsPick(pick)}
                className={`flex min-h-[72px] flex-col items-center justify-center rounded-xl border-2 py-2 touch-manipulation ${
                  rpsPick === pick
                    ? 'border-gold bg-[var(--color-primary)]/15 ring-2 ring-gold/60'
                    : 'border-gold/35 bg-[var(--color-surface)]/12'
                } ${myRpsLocked ? 'opacity-45' : ''}`}
              >
                <span className="text-2xl">{RPS_EMOJI[pick]}</span>
                <span className="mt-1 text-xs font-bold text-[var(--color-ink)]">{label}</span>
              </button>
            ))}
          </div>
          <button
            type="button"
            disabled={rpsPick === null || myRpsLocked}
            onClick={submitRps}
            className="btn-crimson vintage-border mt-3 min-h-[48px] w-full rounded-xl py-3 text-base font-bold"
          >
            {myRpsLocked ? 'بانتظار الخصم…' : 'تأكيد'}
          </button>
        </div>
      ) : null}

      {step === 'closest' ? (
        <div className={shellClass}>
          <h2 className="font-amiri text-center text-lg font-black text-[var(--color-primary)]">أقرب تخمين</h2>
          <div className="mt-3 rounded-xl border-2 border-gold/45 bg-[var(--color-surface)]/10 px-3 py-4">
            <p className="font-amiri text-center text-base font-bold leading-relaxed text-[var(--color-ink)] sm:text-lg">
              {String((tb?.closestQuestion as { text?: string } | undefined)?.text ?? 'سؤال رقمي')}
            </p>
          </div>
          <p className="mt-2 text-center text-xs leading-snug text-[var(--color-ink)]/65">
            سنة، عدد، أو درجة — الأقرب للصحيح يفوز
          </p>
          <input
            type="text"
            inputMode="numeric"
            dir="ltr"
            value={closestVal}
            disabled={myClosestDone}
            onChange={(e) => {
              const raw = e.target.value.replace(/[^\d-]/g, '');
              const sign = raw.startsWith('-') ? '-' : '';
              const digits = raw.replace(/-/g, '');
              setClosestVal(sign + digits);
            }}
            className="mt-4 min-h-[52px] w-full rounded-xl border-2 border-gold/50 bg-white px-4 py-3 text-center text-2xl font-mono font-bold text-[var(--color-ink)] shadow-inner outline-none focus:border-gold focus:ring-2 focus:ring-gold/30"
            placeholder="أدخل رقماً"
          />
          <button
            type="button"
            disabled={myClosestDone || !closestVal.trim()}
            onClick={submitClosest}
            className="btn-crimson vintage-border mt-4 min-h-[48px] w-full rounded-xl py-3 text-base font-bold disabled:opacity-40"
          >
            {myClosestDone ? 'تم الإرسال' : 'إرسال'}
          </button>
        </div>
      ) : null}
    </div>
  );
}
