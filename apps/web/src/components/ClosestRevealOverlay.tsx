import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Crown, Target } from 'lucide-react';
import { auth } from '../firebase';
import type { MatchState } from '../types';

export type ClosestRevealPayload = {
  target: number;
  guesses: Record<string, number>;
  questionText: string;
  winnerUid: string;
  attackerUid: string;
  defenderUid: string;
};

function nameOf(match: MatchState, uid: string) {
  return match.players.find((p) => p.uid === uid)?.name ?? 'لاعب';
}

function colorOf(match: MatchState, uid: string) {
  return match.players.find((p) => p.uid === uid)?.color ?? '#d4af37';
}

export default function ClosestRevealOverlay({
  match,
  payload,
}: {
  match: MatchState;
  payload: ClosestRevealPayload;
}) {
  const myUid = auth.currentUser?.uid ?? '';
  const { target, guesses, questionText, winnerUid, attackerUid, defenderUid } = payload;
  const revealKey = `${target}-${winnerUid}-${attackerUid}`;
  const ga = guesses[attackerUid];
  const gd = guesses[defenderUid];
  const da = typeof ga === 'number' ? Math.abs(ga - target) : null;
  const dd = typeof gd === 'number' ? Math.abs(gd - target) : null;
  const aName = nameOf(match, attackerUid);
  const dName = nameOf(match, defenderUid);
  const aCol = colorOf(match, attackerUid);
  const dCol = colorOf(match, defenderUid);
  const iWon = myUid !== '' && winnerUid === myUid;

  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={revealKey}
        className="fixed inset-0 z-[225] flex items-center justify-center bg-[#0a0505]/92 p-3 backdrop-blur-md sm:p-6"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
      >
        <div className="pointer-events-none absolute inset-0 overflow-hidden">
          {[...Array(16)].map((_, i) => (
            <motion.div
              key={i}
              className="absolute left-1/2 top-1/2 h-[120%] w-1 origin-top rounded-full bg-gradient-to-b from-gold/50 to-transparent"
              style={{ rotate: `${i * 22.5}deg` }}
              initial={{ opacity: 0, scaleY: 0.3 }}
              animate={{ opacity: [0, 0.4, 0.15], scaleY: [0.3, 1, 0.95] }}
              transition={{ duration: 1.2, delay: i * 0.04, ease: 'easeOut' }}
            />
          ))}
        </div>

        <motion.div
          className="relative z-[1] w-full max-w-[min(100%,400px)] rounded-2xl border-2 border-gold/60 bg-[var(--color-background)] p-4 shadow-[0_0_60px_rgba(212,175,55,0.35)] sm:max-w-md sm:p-6"
          initial={{ scale: 0.85, y: 24 }}
          animate={{ scale: 1, y: 0 }}
          transition={{ type: 'spring', stiffness: 200, damping: 22 }}
        >
          <div className="flex items-center justify-center gap-2">
            <Target className="h-5 w-5 text-[var(--color-primary)]" strokeWidth={2.5} />
            <p className="font-mono text-[10px] font-bold tracking-[0.25em] text-[var(--color-primary)]">
              نتيجة أقرب تخمين
            </p>
          </div>

          <p className="font-amiri mt-3 text-center text-base font-bold leading-relaxed text-[var(--color-ink)] sm:text-lg">
            {questionText}
          </p>

          <motion.div
            className="relative mx-auto mt-5 flex w-36 flex-col items-center justify-center rounded-2xl border-2 border-gold/70 bg-gradient-to-b from-gold/25 to-[var(--color-primary)]/15 py-5 sm:w-40"
            animate={{
              boxShadow: [
                '0 0 20px rgba(212,175,55,0.35)',
                '0 0 36px rgba(212,175,55,0.55)',
                '0 0 20px rgba(212,175,55,0.35)',
              ],
            }}
            transition={{ duration: 2, repeat: Infinity }}
          >
            <span className="font-mono text-[9px] font-bold text-[var(--color-ink)]/55">الصحيح</span>
            <span className="font-amiri text-4xl font-black tabular-nums text-[var(--color-primary)] sm:text-5xl">
              {target}
            </span>
          </motion.div>

          <div className="mt-6 grid grid-cols-2 gap-3">
            <motion.div
              className="relative overflow-hidden rounded-xl border-2 p-3 text-center"
              style={{
                borderColor: winnerUid === attackerUid ? '#d4af37' : aCol,
                backgroundColor: winnerUid === attackerUid ? 'rgba(212,175,55,0.12)' : `${aCol}10`,
              }}
              animate={winnerUid === attackerUid ? { scale: [1, 1.02, 1] } : {}}
              transition={{ duration: 0.6, repeat: winnerUid === attackerUid ? Infinity : 0, repeatDelay: 1.2 }}
            >
              {winnerUid === attackerUid ? (
                <motion.div
                  className="absolute left-1/2 top-1 -translate-x-1/2 text-gold"
                  initial={{ scale: 0, rotate: -30 }}
                  animate={{ scale: 1, rotate: 0 }}
                  transition={{ type: 'spring', delay: 0.2 }}
                >
                  <Crown className="h-7 w-7 drop-shadow-md sm:h-8 sm:w-8" strokeWidth={1.5} />
                </motion.div>
              ) : null}
              <p className="truncate pt-5 text-[10px] font-bold text-[var(--color-ink)]/55 sm:pt-6">مهاجم</p>
              <p className="truncate text-sm font-bold text-[var(--color-ink)]">{aName}</p>
              <p className="font-amiri mt-2 text-3xl font-black tabular-nums text-[var(--color-ink)]">
                {ga ?? '—'}
              </p>
              {da != null ? (
                <p className="mt-1 font-mono text-[10px] text-[var(--color-ink)]/50">فرق: {da}</p>
              ) : null}
            </motion.div>

            <motion.div
              className="relative overflow-hidden rounded-xl border-2 p-3 text-center"
              style={{
                borderColor: winnerUid === defenderUid ? '#d4af37' : dCol,
                backgroundColor: winnerUid === defenderUid ? 'rgba(212,175,55,0.12)' : `${dCol}10`,
              }}
              animate={winnerUid === defenderUid ? { scale: [1, 1.02, 1] } : {}}
              transition={{ duration: 0.6, repeat: winnerUid === defenderUid ? Infinity : 0, repeatDelay: 1.2 }}
            >
              {winnerUid === defenderUid ? (
                <motion.div
                  className="absolute left-1/2 top-1 -translate-x-1/2 text-gold"
                  initial={{ scale: 0, rotate: -30 }}
                  animate={{ scale: 1, rotate: 0 }}
                  transition={{ type: 'spring', delay: 0.2 }}
                >
                  <Crown className="h-7 w-7 drop-shadow-md sm:h-8 sm:w-8" strokeWidth={1.5} />
                </motion.div>
              ) : null}
              <p className="truncate pt-5 text-[10px] font-bold text-[var(--color-ink)]/55 sm:pt-6">مدافع</p>
              <p className="truncate text-sm font-bold text-[var(--color-ink)]">{dName}</p>
              <p className="font-amiri mt-2 text-3xl font-black tabular-nums text-[var(--color-ink)]">
                {gd ?? '—'}
              </p>
              {dd != null ? (
                <p className="mt-1 font-mono text-[10px] text-[var(--color-ink)]/50">فرق: {dd}</p>
              ) : null}
            </motion.div>
          </div>

          <motion.p
            className={`font-amiri mt-5 text-center text-lg font-black sm:text-xl ${
              iWon ? 'text-emerald-700' : 'text-[var(--color-primary)]'
            }`}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.35 }}
          >
            {iWon ? '🏆 فزت بالمواجهة!' : `الفائز: ${nameOf(match, winnerUid)}`}
          </motion.p>

          <p className="mt-2 text-center font-mono text-[10px] text-[var(--color-ink)]/45">نعود للخريطة…</p>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
}
