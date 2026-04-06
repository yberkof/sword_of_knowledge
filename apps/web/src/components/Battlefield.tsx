import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Socket } from 'socket.io-client';
import {
  Sword,
  Shield,
  Timer,
  Trophy,
  X,
  Hammer,
  Eye,
  RotateCcw,
  UsersRound,
  Lock,
} from 'lucide-react';
import { auth } from '../firebase';
import { MatchState, Question, HexData } from '../types';
import { getAttackableHexIds, getExpansionPickableHexIds } from '../hexGrid';
import WorldMapBoard from './WorldMapBoard';
import TiebreakerOverlay from './TiebreakerOverlay';
import ClosestRevealOverlay, { type ClosestRevealPayload } from './ClosestRevealOverlay';
import { tiebreakerGameLabel } from '../tieBreakerMeta';

interface BattlefieldProps {
  onEnd: () => void;
  /** Shared connection from GameSession (required). */
  gameSocket: Socket;
  initialMatch: MatchState;
}

type ChatLine = { uid: string; name: string; message: string; ts: number };

/** Socket/Postgres may send options as array or object; keep duel UI from breaking. */
function normalizeDuelQuestion(raw: unknown): Question | null {
  if (!raw || typeof raw !== 'object') return null;
  const r = raw as Record<string, unknown>;
  const text = String(r.text ?? '').trim();
  const id = String(r.id ?? 'q');
  const rawOpts = r.options;
  let options: string[] = [];
  if (Array.isArray(rawOpts)) {
    options = rawOpts.map((o) => String(o));
  } else if (rawOpts && typeof rawOpts === 'object') {
    options = Object.keys(rawOpts as object)
      .filter((k) => /^\d+$/.test(k))
      .sort((a, b) => Number(a) - Number(b))
      .map((k) => String((rawOpts as Record<string, unknown>)[k]));
  }
  if (!text || options.length === 0) return null;
  return {
    id,
    text,
    options,
    category: String(r.category ?? 'عام'),
    difficulty: String(r.difficulty ?? ''),
  };
}

export default function Battlefield({ onEnd, gameSocket, initialMatch }: BattlefieldProps) {
  const onEndRef = useRef(onEnd);
  onEndRef.current = onEnd;
  const socket = gameSocket;
  const [match, setMatch] = useState<MatchState>(initialMatch);
  const [currentQuestion, setCurrentQuestion] = useState<Question | null>(null);
  const [selectedOption, setSelectedOption] = useState<number | null>(null);
  /** Seconds remaining (server-synced via phaseEndsAt) */
  const [timerSec, setTimerSec] = useState(10);
  const [duelDurationMs, setDuelDurationMs] = useState(10_000);
  const phaseEndsAtRef = useRef<number | null>(null);
  const serverOffsetRef = useRef(0);
  const [showResult, setShowResult] = useState(false);
  const [duelResult, setDuelResult] = useState<any>(null);
  const [hiddenOptionIndices, setHiddenOptionIndices] = useState<number[]>([]);
  const [duelHammerConsumed, setDuelHammerConsumed] = useState(false);
  const duelAutoSubmitRef = useRef(false);
  const [chatMessages, setChatMessages] = useState<ChatLine[]>([]);
  const [chatDraft, setChatDraft] = useState('');
  const [mapToast, setMapToast] = useState<string | null>(null);
  /** neutral-hex duels: only the attacker answers; the other player watches. */
  const [duelRole, setDuelRole] = useState<'attacker' | 'defender' | 'spectate' | null>(null);
  const [expansionText, setExpansionText] = useState('');
  const [expansionMeta, setExpansionMeta] = useState<{
    round: number;
    maxRounds: number;
    phaseEndsAt: number;
    questionText: string;
  } | null>(null);
  const [expansionTimerSec, setExpansionTimerSec] = useState(18);
  const [audiencePct, setAudiencePct] = useState<number[] | null>(null);
  const [spyglassIdx, setSpyglassIdx] = useState<number | null>(null);
  const [safetyOn, setSafetyOn] = useState(false);
  const [closestReveal, setClosestReveal] = useState<ClosestRevealPayload | null>(null);

  useEffect(() => {
    setMatch(initialMatch);
  }, [initialMatch]);

  const closestRevealTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (!closestReveal) return;
    if (closestRevealTimerRef.current) clearTimeout(closestRevealTimerRef.current);
    closestRevealTimerRef.current = setTimeout(() => {
      setClosestReveal(null);
      closestRevealTimerRef.current = null;
    }, 4600);
    return () => {
      if (closestRevealTimerRef.current) {
        clearTimeout(closestRevealTimerRef.current);
        closestRevealTimerRef.current = null;
      }
    };
  }, [closestReveal]);

  useEffect(() => {
    const newSocket = gameSocket;

    const onRoomUpdate = (updatedMatch: MatchState) => {
      setMatch(updatedMatch);
    };

    const onGameStart = (room: MatchState) => {
      setMatch(room);
    };

    newSocket.on('room_update', onRoomUpdate);
    newSocket.on('game_start', onGameStart);

    newSocket.on('tiebreaker_closest_reveal', (p: Record<string, unknown>) => {
      const guesses = p.guesses;
      if (!guesses || typeof guesses !== 'object') return;
      setClosestReveal({
        target: Number(p.target),
        guesses: guesses as Record<string, number>,
        questionText: String(p.questionText ?? ''),
        winnerUid: String(p.winnerUid ?? ''),
        attackerUid: String(p.attackerUid ?? ''),
        defenderUid: String(p.defenderUid ?? ''),
      });
    });

    newSocket.on('expansion_round_start', (payload: any) => {
      const clientReceive = Date.now();
      const serverNow = Number(payload.serverNowMs) || clientReceive;
      serverOffsetRef.current = serverNow - clientReceive;
      const endsAt = Number(payload.phaseEndsAt) || clientReceive + 18_000;
      phaseEndsAtRef.current = endsAt;
      setExpansionMeta({
        round: Number(payload.round) || 1,
        maxRounds: Number(payload.maxRounds) || 2,
        phaseEndsAt: endsAt,
        questionText: String(payload.questionText || ''),
      });
      setExpansionText('');
      setAudiencePct(null);
      setSpyglassIdx(null);
      setSafetyOn(false);
    });

    newSocket.on('expansion_pick_phase', (payload: { room?: MatchState; pickQueue?: string[] }) => {
      setExpansionMeta(null);
      setExpansionText('');
      if (payload?.room && typeof payload.room === 'object') {
        setMatch(payload.room);
      }
    });

    newSocket.on('expansion_complete', () => {
      setExpansionMeta(null);
    });

    newSocket.on('duel_audience_hint', (p: { percentages?: number[] }) => {
      if (Array.isArray(p?.percentages)) setAudiencePct(p.percentages);
    });

    newSocket.on('duel_spyglass_hint', (p: { suggestedIndex?: number }) => {
      if (typeof p?.suggestedIndex === 'number') setSpyglassIdx(p.suggestedIndex);
    });

    newSocket.on('duel_safety_locked', () => {
      setSafetyOn(true);
    });

    newSocket.on('join_rejected', () => {
      setMapToast('الغرفة ممتلئة (4 لاعبين كحد أقصى).');
    });

    newSocket.on('expansion_pick_invalid', (p: { reason?: string }) => {
      const r = p?.reason;
      const msg =
        r === 'not_your_pick'
          ? 'ليس دورك لاختيار الأرض.'
          : r === 'not_adjacent'
            ? 'اختر أرضاً محايدة مجاورة لمناطقك.'
            : r === 'not_neutral'
              ? 'هذه الأرض ليست متاحة.'
              : r === 'not_pick_phase'
                ? 'مرحلة الاختيار غير نشطة.'
                : 'لا يمكن اختيار هذه الخلية.';
      setMapToast(msg);
    });

    newSocket.on('duel_start', (payload: any) => {
      setClosestReveal(null);
      const clientReceive = Date.now();
      const serverNow = Number(payload.serverNowMs) || clientReceive;
      serverOffsetRef.current = serverNow - clientReceive;
      const endsAt = Number(payload.phaseEndsAt) || clientReceive + 10_000;
      const duration = Number(payload.duelDurationMs) || 10_000;
      phaseEndsAtRef.current = endsAt;
      setDuelDurationMs(duration);
      setHiddenOptionIndices(Array.isArray(payload.hiddenOptionIndices) ? payload.hiddenOptionIndices : []);
      setDuelHammerConsumed(Boolean(payload.duelHammerConsumed));
      setAudiencePct(null);
      setSpyglassIdx(null);
      setSafetyOn(false);
      duelAutoSubmitRef.current = false;
      const uid = auth.currentUser?.uid ?? '';
      const aUid = payload.attackerUid != null ? String(payload.attackerUid) : '';
      const dUid = payload.defenderUid != null ? String(payload.defenderUid) : '';
      if (dUid === 'neutral' && uid && aUid && uid !== aUid) {
        setDuelRole('spectate');
      } else if (uid && aUid && uid === aUid) {
        setDuelRole('attacker');
      } else if (uid && dUid && dUid !== 'neutral' && uid === dUid) {
        setDuelRole('defender');
      } else {
        setDuelRole(null);
      }
      const q = normalizeDuelQuestion(payload?.question);
      if (q) {
        setCurrentQuestion(q);
      } else {
        setCurrentQuestion(null);
        setMapToast('تعذّر تحميل نص السؤال. انتظر تحديث الغرفة أو أعد المحاولة.');
      }
      setSelectedOption(null);
      setShowResult(false);
      setDuelResult(null);
    });

    newSocket.on('duel_resolved', ({ room, result }: { room: MatchState, result: any }) => {
      setMatch(room);
      setCurrentQuestion(null);
      setSelectedOption(null);
      setDuelRole(null);
      phaseEndsAtRef.current = null;
      setHiddenOptionIndices([]);
      setDuelHammerConsumed(false);
      const skipDuelFlash = Boolean(result?.tieBreakerMinigame && result?.minigame === 'closest');
      if (skipDuelFlash) {
        setDuelResult(null);
        setShowResult(false);
        return;
      }
      setDuelResult(result);
      setShowResult(true);
      setTimeout(() => {
        setShowResult(false);
        setDuelResult(null);
      }, 3000);
    });

    newSocket.on(
      'duel_options_update',
      (payload: { hiddenOptionIndices?: number[]; duelHammerConsumed?: boolean }) => {
        if (Array.isArray(payload.hiddenOptionIndices)) {
          setHiddenOptionIndices(payload.hiddenOptionIndices);
        }
        if (payload.duelHammerConsumed) setDuelHammerConsumed(true);
      }
    );

    newSocket.on('attack_blocked', ({ targetHexId }: { targetHexId: number }) => {
      alert('تم صد الهجوم بواسطة الدرع!');
    });

    const onAttackInvalid = (payload: { reason?: string }) => {
      const r = payload?.reason;
      const msg =
        r === 'not_adjacent'
          ? 'اضغط خلية مجاورة لأراضيك (المُضيئة بالبرتقالي).'
          : r === 'not_your_turn'
            ? 'ليس دورك الآن.'
            : r === 'own_territory'
              ? 'لا يمكنك مهاجمة أرضك.'
              : 'لا يمكن تنفيذ هذه الحركة.';
      setMapToast(msg);
    };
    newSocket.on('attack_invalid', onAttackInvalid);

    newSocket.on(
      'game_ended',
      ({
        winnerUid,
        rankings,
        room,
      }: {
        winnerUid: string;
        rankings?: { uid: string; place: number }[];
        room: MatchState;
      }) => {
        setMatch(room);
        setCurrentQuestion(null);
        setExpansionMeta(null);
        setDuelRole(null);
        setDuelResult({
          gameEnd: true,
          winnerUid: String(winnerUid),
          rankings: Array.isArray(rankings) ? rankings : [],
        });
        setShowResult(true);
        setTimeout(() => {
          onEndRef.current();
        }, 4500);
      }
    );

    newSocket.on(
      'room_chat',
      (msg: { uid: string; name: string; message: string; ts: number }) => {
        setChatMessages((prev) => [...prev.slice(-40), msg]);
      }
    );

    return () => {
      newSocket.off('room_update', onRoomUpdate);
      newSocket.off('game_start', onGameStart);
      newSocket.off('duel_start');
      newSocket.off('duel_resolved');
      newSocket.off('duel_options_update');
      newSocket.off('attack_blocked');
      newSocket.off('attack_invalid', onAttackInvalid);
      newSocket.off('game_ended');
      newSocket.off('room_chat');
      newSocket.off('expansion_round_start');
      newSocket.off('expansion_pick_phase');
      newSocket.off('expansion_complete');
      newSocket.off('duel_audience_hint');
      newSocket.off('duel_spyglass_hint');
      newSocket.off('duel_safety_locked');
      newSocket.off('join_rejected');
      newSocket.off('expansion_pick_invalid');
    };
  }, [gameSocket]);

  useEffect(() => {
    if (!expansionMeta || match.phase !== 'expansion') return;
    const tick = () => {
      const ends = expansionMeta.phaseEndsAt;
      const approx = Date.now() + serverOffsetRef.current;
      const remain = Math.max(0, ends - approx);
      setExpansionTimerSec(Math.ceil(remain / 1000));
    };
    tick();
    const id = setInterval(tick, 500);
    return () => clearInterval(id);
  }, [expansionMeta, match.phase]);

  const handleAnswer = useCallback(
    (index: number) => {
      if (duelRole === 'spectate') return;
      if (selectedOption !== null || !match) return;
      if (hiddenOptionIndices.includes(index)) return;
      const uid = auth.currentUser?.uid;
      if (!uid) return;
      setSelectedOption(index);
      socket.emit('submit_answer', {
        roomId: match.id,
        uid,
        answerIndex: index,
      });
    },
    [match, socket, selectedOption, hiddenOptionIndices, duelRole]
  );

  const handleDuelHammer = useCallback(() => {
    if (duelRole === 'spectate') return;
    if (!match || !currentQuestion || duelHammerConsumed) return;
    const uid = auth.currentUser?.uid;
    if (!uid) return;
    socket.emit('use_powerup', {
      roomId: match.id,
      uid,
      powerupType: 'hammer',
    });
  }, [match, socket, currentQuestion, duelHammerConsumed, duelRole]);

  const emitDuelPower = useCallback(
    (powerupType: string) => {
      if (duelRole === 'spectate') return;
      const uid = auth.currentUser?.uid;
      if (!uid || !match) return;
      socket.emit('use_powerup', { roomId: match.id, uid, powerupType });
    },
    [match, socket, duelRole]
  );

  // Server-synchronized countdown: phaseEndsAt + clock offset (FR-GAME-04).
  useEffect(() => {
    if (!currentQuestion || !phaseEndsAtRef.current) return;
    const tick = () => {
      const ends = phaseEndsAtRef.current;
      if (!ends) return;
      const approxServerNow = Date.now() + serverOffsetRef.current;
      const remainMs = Math.max(0, ends - approxServerNow);
      const sec = Math.ceil(remainMs / 1000);
      setTimerSec(sec);
      if (duelRole === 'spectate') return;
      if (selectedOption !== null) return;
      if (remainMs <= 0 && !duelAutoSubmitRef.current) {
        duelAutoSubmitRef.current = true;
        const uid = auth.currentUser?.uid;
        if (uid && match) {
          setSelectedOption(-1);
          socket.emit('submit_answer', { roomId: match.id, uid, answerIndex: -1 });
        }
      }
    };
    tick();
    const interval = setInterval(tick, 250);
    return () => clearInterval(interval);
  }, [currentQuestion, selectedOption, match, socket, duelRole]);

  const [powerUpMode, setPowerUpMode] = useState<string | null>(null);

  useEffect(() => {
    if (!mapToast) return;
    const t = setTimeout(() => setMapToast(null), 4500);
    return () => clearTimeout(t);
  }, [mapToast]);

  const handlePowerUp = (type: string) => {
    if (!match || match.phase !== 'conquest') return;
    const user = auth.currentUser;
    if (turnPlayer?.uid !== user?.uid) return;

    if (type === 'hammer') {
      setPowerUpMode('hammer');
      alert('اختر منطقة لتدميرها بالمطرقة');
    } else if (type === 'shield') {
      setPowerUpMode('shield');
      alert('اختر منطقة لحمايتها بالدرع');
    } else if (type === 'spyglass') {
      alert('المنظار غير متاح أثناء المبارزة (لا تُعرض الإجابة قبل نهاية الوقت).');
    }
  };

  const myUid = auth.currentUser?.uid ?? '';
  const turnPlayer = match.players[match.currentTurnIndex] ?? match.players.find((p) => !p.isCapitalLost);
  const isMyTurn = turnPlayer?.uid === myUid;

  const attackableHexIds = useMemo(() => {
    if (!match || match.phase !== 'conquest' || !myUid) return new Set<number>();
    return getAttackableHexIds(match.mapState, myUid);
  }, [match, myUid]);

  const expansionPickable = useMemo(() => {
    if (!match || match.phase !== 'expansion' || match.expansion?.phase !== 'pick' || !myUid) {
      return new Set<number>();
    }
    const pq = match.expansion.pickQueue;
    if (!pq?.length || pq[0] !== myUid) return new Set<number>();
    return getExpansionPickableHexIds(match.mapState, myUid);
  }, [match, myUid]);

  const handleHexClick = useCallback(
    (hexId: number) => {
      if (!match) return;
      const user = auth.currentUser;
      if (!user) {
        setMapToast('سجّل الدخول للعب.');
        return;
      }

      const hex = match.mapState.find((h) => h.id === hexId);
      if (!hex) return;

      if (match.phase === 'expansion' && match.expansion?.phase === 'question') {
        setMapToast('أكمل التخمين في النافذة أعلاه ثم اضغط «إرسال التخمين».');
        return;
      }

      if (match.phase === 'expansion' && match.expansion?.phase === 'pick') {
        const pq = match.expansion.pickQueue;
        if (!pq?.length || pq[0] !== user.uid) {
          setMapToast('انتظر — ليس دورك لاختيار أرض.');
          return;
        }
        if (!expansionPickable.has(hexId)) {
          setMapToast('اختر أرضاً محايدة مجاورة لمناطقك (الخلايا المُضيئة).');
          return;
        }
        socket.emit('expansion_pick_hex', {
          roomId: match.id,
          uid: user.uid,
          hexId,
        });
        return;
      }

      if (match.phase === 'duel') {
        setMapToast('مبارزة جارية — أجب على السؤال في النافذة.');
        return;
      }

      if (match.phase === 'tiebreaker') {
        setMapToast('مواجهة فاصلة جارية — تفاعل مع النافذة.');
        return;
      }

      if (match.phase !== 'conquest') {
        setMapToast('لا يمكن اللعب في هذه المرحلة.');
        return;
      }

      const turnUid = match.players[match.currentTurnIndex]?.uid;
      if (turnUid !== user.uid) {
        setMapToast('ليس دورك الآن.');
        return;
      }

      if (powerUpMode === 'hammer') {
        if (hex.isCapital) {
          setMapToast('المطرقة لا تستهدف العاصمة.');
          return;
        }
        socket.emit('use_powerup', {
          roomId: match.id,
          uid: user.uid,
          powerupType: 'hammer',
          targetHexId: hexId,
        });
        setPowerUpMode(null);
        return;
      }
      if (powerUpMode === 'shield') {
        if (hex.ownerUid !== user.uid) {
          setMapToast('الدرع يُطبَّق على أرضك فقط.');
          return;
        }
        socket.emit('use_powerup', {
          roomId: match.id,
          uid: user.uid,
          powerupType: 'shield',
          targetHexId: hexId,
        });
        setPowerUpMode(null);
        return;
      }

      if (!attackableHexIds.has(hexId)) {
        setMapToast('اضغط أرضاً يمكن مهاجمتها (حدّ برتقالي، مجاورة لأراضيك).');
        return;
      }

      socket.emit('attack', {
        roomId: match.id,
        attackerUid: user.uid,
        targetHexId: hexId,
      });
    },
    [match, socket, expansionPickable, attackableHexIds, powerUpMode]
  );

  const hexInteractionDisabled = (hex: HexData): boolean => {
    if (match.phase === 'expansion' && match.expansion?.phase === 'pick') {
      const pq = match.expansion.pickQueue;
      if (!pq?.length || pq[0] !== myUid) return true;
      if (hex.ownerUid != null) return true;
      return !expansionPickable.has(hex.id);
    }
    if (match.phase === 'tiebreaker') return true;
    if (!isMyTurn || match.phase !== 'conquest') return true;
    if (powerUpMode === 'hammer') return hex.isCapital;
    if (powerUpMode === 'shield') return hex.ownerUid !== myUid;
    return !attackableHexIds.has(hex.id);
  };

  let duelOutcomeOverlay: React.ReactNode = null;
  if (showResult && duelResult) {
    if (duelResult.gameEnd) {
      const iWon = myUid !== '' && duelResult.winnerUid === myUid;
      const rk: { uid: string; place: number }[] = Array.isArray(duelResult.rankings)
        ? duelResult.rankings
        : [];
      duelOutcomeOverlay = (
        <motion.div
          key="game-end"
          initial={{ opacity: 0, scale: 2 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.5 }}
          className={`fixed inset-0 z-[200] flex flex-col items-center justify-center ${
            iWon ? 'bg-primary' : 'bg-surface'
          } text-background`}
        >
          <Trophy className="mb-4 h-32 w-32 animate-bounce" />
          <h2 className="font-amiri px-2 text-center text-5xl font-black uppercase italic">
            {iWon ? 'انتصرت في المعركة!' : 'انتهت المعركة'}
          </h2>
          {rk.length > 0 ? (
            <ul className="mt-6 max-h-40 space-y-1 overflow-y-auto px-4 text-center text-sm">
              {rk.map((row) => (
                <li key={row.uid}>
                  المركز {row.place}: {match.players.find((p) => p.uid === row.uid)?.name ?? row.uid.slice(0, 6)}
                </li>
              ))}
            </ul>
          ) : null}
          <p className="text-background/70 mt-4 font-mono text-[10px]">مكافآت XP والألقاب تُحدَّث في ملفك</p>
        </motion.div>
      );
    } else {
    const viewerUid = auth.currentUser?.uid ?? '';
    const isAttacker = viewerUid !== '' && duelResult.attackerUid === viewerUid;
    const spectatingNeutral =
      viewerUid !== '' &&
      duelResult.defenderUid === 'neutral' &&
      duelResult.attackerUid !== viewerUid;
    const iWon = viewerUid !== '' && duelResult.winnerUid === viewerUid;
    const attackerName =
      match.players.find((p) => p.uid === duelResult.attackerUid)?.name ?? 'المهاجم';
    const bothCorrect = Boolean(duelResult.attackerCorrect && duelResult.defenderCorrect);
    const wonBySpeed = Boolean(duelResult.wonBySpeed);

    let title: string;
    let subtitle: string;
    if (duelResult.tieBreakerMinigame) {
      const mgLabel = tiebreakerGameLabel(String(duelResult.minigame || ''));
      title = iWon ? 'فوز في المواجهة الفاصلة!' : 'انتهت المواجهة الفاصلة';
      subtitle = iWon ? `تغلّبت على خصمك في ${mgLabel}` : `فاز عليك الخصم في ${mgLabel}`;
    } else if (spectatingNeutral) {
      title = duelResult.attackerWins ? `${attackerName} يوسّع أراضيه!` : 'بقيت الأرض حُرّة';
      subtitle = duelResult.attackerWins
        ? 'سُجّلت غزوة في سجلّ المعركة'
        : 'لم يُحتلُّ الحِصن بعد';
    } else {
      title = iWon
        ? isAttacker
          ? 'انتصار!'
          : 'دفاع ناجح!'
        : isAttacker
          ? 'فشل الهجوم!'
          : 'فقدتَ المنطقة!';
      const attackerLostWhy = !duelResult.attackerCorrect
        ? 'إجابة خاطئة أو انتهى الوقت'
        : bothCorrect && wonBySpeed && duelResult.defenderCorrect
          ? 'الخصم سبقك بإجابةٍ صحيحة'
          : 'لم تُحسم المبارزة لصالحك';
      const defenderLostWhy =
        bothCorrect && wonBySpeed
          ? 'المهاجم سبقك بإجابةٍ صحيحة'
          : 'المهاجم أصاب والتقط الأرض';
      subtitle = iWon
        ? isAttacker
          ? 'تم احتلال المنطقة'
          : 'حافظتَ على أرضك'
        : isAttacker
          ? attackerLostWhy
          : defenderLostWhy;
    }
    const moodGood = duelResult.tieBreakerMinigame
      ? iWon
      : spectatingNeutral
        ? !duelResult.attackerWins
        : iWon;
    duelOutcomeOverlay = (
      <motion.div
        key="duel-outcome"
        initial={{ opacity: 0, scale: 2 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.5 }}
        className={`fixed inset-0 z-[200] flex flex-col items-center justify-center ${
          moodGood ? 'bg-primary' : 'bg-red-800'
        } text-background`}
      >
        {moodGood ? (
          <Trophy className="mb-4 h-32 w-32 animate-bounce" />
        ) : (
          <X className="mb-4 h-32 w-32 animate-pulse" />
        )}
        <h2 className="font-amiri px-2 text-center text-5xl leading-none font-black tracking-tighter uppercase italic sm:text-6xl">
          {title}
        </h2>
        <div className="mt-8 flex gap-8">
          {spectatingNeutral ? (
            <div className="flex flex-col items-center">
              <span className="text-[10px] font-bold uppercase opacity-50">المهاجم</span>
              <span className={`text-xl font-black ${duelResult.attackerCorrect ? 'text-emerald-200' : 'text-red-200'}`}>
                {duelResult.attackerCorrect ? '✓' : '✗'}
              </span>
              <span className="mt-1 text-[9px] opacity-60">أرضٌ محايدة — لا منازِع</span>
            </div>
          ) : (
            <>
              <div className="flex flex-col items-center">
                <span className="text-[10px] font-bold uppercase opacity-50">المهاجم</span>
                <span className={`text-xl font-black ${duelResult.attackerCorrect ? 'text-emerald-200' : 'text-red-200'}`}>
                  {duelResult.attackerCorrect ? '✓' : '✗'}
                </span>
              </div>
              <div className="flex flex-col items-center">
                <span className="text-[10px] font-bold uppercase opacity-50">المدافع</span>
                <span className={`text-xl font-black ${duelResult.defenderCorrect ? 'text-emerald-200' : 'text-red-200'}`}>
                  {duelResult.defenderCorrect ? '✓' : '✗'}
                </span>
              </div>
            </>
          )}
        </div>
        <p className="mt-8 max-w-md px-4 text-center font-mono text-xs tracking-widest text-background/85 uppercase sm:text-sm">
          {subtitle}
        </p>
        {duelResult.correctAnswerText ? (
          <p className="mt-4 px-4 text-center text-sm text-background/90">
            الإجابة الصحيحة: <strong>{duelResult.correctAnswerText}</strong>
          </p>
        ) : null}
        {duelResult.tieBreakerMinigame && duelResult.minigame === 'closest' && duelResult.closestTarget != null ? (
          <p className="mt-4 max-w-md px-4 text-center text-sm text-background/90">
            {duelResult.closestQuestionText ? (
              <span className="block font-amiri text-background/80">{String(duelResult.closestQuestionText)}</span>
            ) : null}
            <span className="mt-1 block font-mono">
              الرقم الصحيح: <strong>{duelResult.closestTarget}</strong>
            </span>
          </p>
        ) : null}
      </motion.div>
    );
    }
  }

  const sendChat = () => {
    if (!match || !chatDraft.trim()) return;
    const u = auth.currentUser;
    if (!u) return;
    socket.emit('room_chat', {
      roomId: match.id,
      uid: u.uid,
      name: u.displayName || 'Player',
      message: chatDraft.slice(0, 280),
    });
    setChatDraft('');
  };

  const duelTimerProgress =
    duelDurationMs > 0 ? Math.min(1, Math.max(0, timerSec / (duelDurationMs / 1000))) : 0;

  return (
    <div className="battlefield-mobile-root bg-parchment relative flex min-h-0 w-full flex-1 flex-col overflow-hidden text-ink">
      {/* Top Bar: compact on phones, roomier from sm+ */}
      <div
        className={`border-gold/30 bg-surface z-40 flex-none grid gap-1.5 border-b px-2 py-2 text-background shadow-heavy backdrop-blur-md sm:gap-3 sm:px-3 sm:py-3 md:gap-4 md:p-4 ${
          match.players.length >= 4 ? 'grid-cols-2 sm:grid-cols-4' : match.players.length === 3 ? 'grid-cols-3' : 'grid-cols-2'
        }`}
      >
        {match.players.map((p) => (
          <div key={p.uid} className={`flex flex-col gap-0.5 sm:gap-1 ${p.uid === auth.currentUser?.uid ? 'opacity-100' : 'opacity-60'}`}>
            <div className="flex items-center justify-between gap-0.5 sm:gap-1">
              <span className="max-w-[4.5rem] truncate text-[8px] font-bold tracking-wider text-background uppercase sm:max-w-[72px] sm:text-[9px] md:text-[10px]">
                {p.name}
              </span>
              <div className="flex gap-px sm:gap-0.5">
                {[...Array(3)].map((_, j) => (
                  <div
                    key={j}
                    className={`h-1.5 w-1.5 rounded-full sm:h-2 sm:w-2 ${j < p.hp ? 'bg-gold' : 'bg-background/25'}`}
                  />
                ))}
              </div>
            </div>
            <div className="text-gold/90 font-mono text-[8px] sm:text-[9px]">
              نقاط: {match.matchScore?.[p.uid] ?? 0}
            </div>
            <div className="h-0.5 w-full overflow-hidden rounded-full bg-background/20 sm:h-1">
              <div
                className="h-full transition-all duration-500"
                style={{ backgroundColor: p.color, width: `${(p.hp / 3) * 100}%` }}
              />
            </div>
          </div>
        ))}
      </div>

      {match.phase === 'conquest' || match.phase === 'duel' || match.phase === 'tiebreaker' ? (
        <div className="border-gold/20 bg-background/90 text-ink flex flex-none items-center justify-center gap-2 border-b px-2 py-1 font-mono text-[9px] tracking-wider sm:gap-4 sm:px-3 sm:py-2 sm:text-[10px] sm:tracking-widest">
          <span>
            جولة قتال <strong className="text-primary">{match.battleRound ?? 1}</strong>
          </span>
          <span className="text-ink/40">|</span>
          <span>
            هجمات متبقية: <strong className="text-primary">{match.attacksRemainingThisRound ?? 4}</strong> / 4
          </span>
        </div>
      ) : null}

      <AnimatePresence>
        {expansionMeta && match.phase === 'expansion' ? (
          <motion.div
            key="expansion"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[200] flex items-center justify-center bg-background-dark/90 p-4 backdrop-blur-md"
          >
            <div className="vintage-border bg-parchment max-w-md space-y-4 rounded-xl p-6 shadow-heavy">
              <p className="text-primary font-mono text-[10px] font-bold tracking-widest">
                مرحلة الانتزاع — الجولة {expansionMeta.round} / {expansionMeta.maxRounds}
              </p>
              <h3 className="font-amiri text-xl font-bold text-ink">{expansionMeta.questionText}</h3>
              <p className="text-ink/60 text-sm">أدخل رقماً قريباً من الإجابة الصحيحة.</p>
              <div className="flex items-center gap-3">
                <input
                  type="number"
                  dir="ltr"
                  inputMode="decimal"
                  value={expansionText}
                  onChange={(e) => setExpansionText(e.target.value)}
                  className="border-gold/40 focus:border-gold flex-1 rounded-xl border bg-background px-3 py-2 text-start text-ink [unicode-bidi:plaintext]"
                  placeholder="رقم"
                />
                <span className="text-primary font-mono text-2xl font-black">{expansionTimerSec}</span>
              </div>
              <button
                type="button"
                onClick={() => {
                  const u = auth.currentUser;
                  if (!u || !match) return;
                  const v = parseFloat(expansionText);
                  if (!Number.isFinite(v)) return;
                  socket.emit('expansion_submit_number', {
                    roomId: match.id,
                    uid: u.uid,
                    value: v,
                  });
                }}
                className="btn-crimson vintage-border w-full rounded-lg py-3 font-bold uppercase"
              >
                إرسال التخمين
              </button>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>

      {match.phase === 'tiebreaker' && match.tieBreaker ? (
        <TiebreakerOverlay socket={socket} roomId={match.id} match={match} myUid={myUid} />
      ) : null}

      {closestReveal ? <ClosestRevealOverlay match={match} payload={closestReveal} /> : null}

      {/* Main map: mobile-first full width, centered cap on large screens */}
      <div className="relative flex min-h-0 flex-1 items-center justify-center px-1 py-1 sm:px-3 sm:py-2 md:px-4 md:py-3">
        {mapToast ? (
          <div className="border-gold bg-surface/95 absolute top-0.5 left-1/2 z-20 max-w-[94%] -translate-x-1/2 rounded-lg border-2 px-3 py-1.5 text-center text-[11px] font-bold text-gold shadow-heavy sm:top-2 sm:rounded-xl sm:px-4 sm:py-2 sm:text-xs">
            {mapToast}
          </div>
        ) : null}

        <div className="battlefield-classic-shell z-[1] w-full max-w-full sm:max-w-[min(92vw,720px)]">
          <div className="battlefield-classic-crown px-2 py-1.5 sm:px-5 sm:py-2.5">
            <p className="font-amiri text-gold text-center text-xs font-bold tracking-[0.1em] [text-shadow:0_2px_4px_rgba(0,0,0,0.85)] sm:text-base sm:tracking-[0.12em] md:text-lg">
              سيف المعرفة
            </p>
            <p className="text-center font-mono text-[8px] tracking-[0.18em] text-gold/55 uppercase sm:text-[10px] sm:tracking-[0.2em]">
              خريطة المعركة
            </p>
          </div>
          <div className="battlefield-realm battlefield-stars animate-map-torch battlefield-classic-map relative cursor-default overflow-hidden p-2 sm:p-4 md:p-6">
            <div className="relative z-[2] mx-auto w-full max-w-[min(100%,min(96vw,820px))]">
              <WorldMapBoard
                mapState={match.mapState}
                players={match.players}
                attackableIds={attackableHexIds}
                expansionPickableIds={expansionPickable}
                showExpansionPickHighlight={
                  match.phase === 'expansion' &&
                  match.expansion?.phase === 'pick' &&
                  myUid === match.expansion.pickQueue?.[0]
                }
                onRegionClick={handleHexClick}
                isRegionInactive={hexInteractionDisabled}
              />
            </div>
          <p className="text-gold/45 pointer-events-none absolute bottom-1 left-1/2 -translate-x-1/2 text-center font-mono text-[9px] tracking-[0.22em] uppercase sm:bottom-3 sm:text-[11px] sm:tracking-[0.28em]">
            مملكة السِّيف
          </p>
        </div>
        </div>

        {/* Turn hint — below map frame on small screens, overlaid on large */}
        <div className="pointer-events-none absolute bottom-0.5 left-1/2 z-10 flex max-w-[min(98%,680px)] -translate-x-1/2 flex-col items-center gap-1 sm:bottom-3 sm:gap-1.5 md:bottom-5">
          <div className="border-gold/40 bg-surface/90 flex max-w-[95vw] items-center gap-2 rounded-full border px-3 py-1.5 shadow-heavy backdrop-blur-md sm:gap-3 sm:px-6 sm:py-2">
            <div
              className={`h-1.5 w-1.5 shrink-0 animate-pulse rounded-full sm:h-2 sm:w-2`}
              style={{ backgroundColor: turnPlayer?.color ?? '#888' }}
            />
            <span className="text-[10px] font-bold tracking-wide text-background uppercase sm:text-xs sm:tracking-widest">
              {match.phase === 'expansion' && match.expansion?.phase === 'pick'
                ? myUid === match.expansion.pickQueue?.[0]
                  ? 'دورك لاختيار أرض'
                  : `دور الاختيار: ${match.players.find((p) => p.uid === match.expansion?.pickQueue?.[0])?.name ?? '...'}`
                : isMyTurn
                  ? 'دورك الآن'
                  : `دور ${turnPlayer?.name ?? '...'}`}
            </span>
          </div>
          {isMyTurn && match.phase === 'conquest' && !powerUpMode ? (
            <p className="text-ink/70 max-w-[90vw] px-1 text-center font-mono text-[9px] leading-snug sm:px-2 sm:text-[10px]">
              اضغط خلية برتقالية الحدود — يجب أن تجاور أرضك
            </p>
          ) : null}
          {match.phase === 'expansion' && match.expansion?.phase === 'pick' && myUid === match.expansion.pickQueue?.[0] ? (
            <p className="text-ink/80 max-w-[90vw] px-1 text-center font-mono text-[9px] leading-snug sm:px-2 sm:text-[10px]">
              اختر خليةً محايدة مجاورة لأراضيك (مُضيئة)
            </p>
          ) : null}
        </div>
      </div>

      {/* Room chat (FR-SOC) */}
      {match.phase !== 'ended' ? (
        <div className="border-gold/30 bg-background/95 z-30 flex max-h-[3.25rem] flex-none flex-col gap-0.5 border-t px-2 py-1 backdrop-blur-sm sm:max-h-24 sm:gap-1 sm:px-3 sm:py-2">
          <div className="text-ink/80 max-h-9 space-y-0.5 overflow-y-auto font-mono text-[9px] sm:max-h-20 sm:text-[10px]">
            {chatMessages.map((m, i) => (
              <div key={`${m.ts}-${i}`}>
                <span className="text-primary font-bold">{m.name}:</span> {m.message}
              </div>
            ))}
          </div>
          <div className="flex gap-1.5 sm:gap-2">
            <input
              dir="auto"
              value={chatDraft}
              onChange={(e) => setChatDraft(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && sendChat()}
              placeholder="رسالة..."
              className="border-gold/30 focus:border-gold min-h-[40px] flex-1 rounded-lg border bg-background px-2 py-2 text-sm text-ink sm:min-h-0 sm:py-1 sm:text-xs"
            />
            <button
              type="button"
              onClick={sendChat}
              className="text-primary flex min-h-[40px] min-w-[3rem] items-center justify-center px-2 text-[10px] font-bold touch-manipulation sm:min-h-0 sm:min-w-0"
            >
              إرسال
            </button>
          </div>
        </div>
      ) : null}

      {/* Power-ups: horizontal scroll on narrow phones, row on sm+ */}
      <div className="border-gold/30 bg-surface z-40 flex flex-none touch-pan-x items-center justify-start gap-1 overflow-x-auto overscroll-x-contain border-t px-2 py-2 pb-[max(0.5rem,env(safe-area-inset-bottom,0px))] text-background backdrop-blur-md [-webkit-overflow-scrolling:touch] sm:justify-around sm:gap-0 sm:px-4 sm:py-4">
        <PowerUpButton icon={<Hammer />} label="المطرقة" count={2} onClick={() => handlePowerUp('hammer')} />
        <PowerUpButton icon={<Shield />} label="الدرع" count={1} onClick={() => handlePowerUp('shield')} />
        <PowerUpButton icon={<Eye />} label="المنظار" count={3} onClick={() => handlePowerUp('spyglass')} />
      </div>

      {/* Duel Overlay */}
      <AnimatePresence>
        {currentQuestion && (
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            className="fixed inset-0 z-[200] flex items-center justify-center overflow-y-auto bg-background-dark/92 p-3 pt-[max(0.75rem,env(safe-area-inset-top,0px))] backdrop-blur-xl sm:p-6"
          >
            <div className="vintage-border bg-parchment my-auto max-h-[min(92dvh,880px)] w-full max-w-lg space-y-4 overflow-y-auto rounded-xl p-4 shadow-heavy sm:space-y-8 sm:p-6">
              {/* Question Header */}
              <div className="flex items-center justify-between gap-3 sm:gap-4">
                <div className="min-w-0 space-y-0.5 sm:space-y-1">
                  <p className="text-primary text-[9px] font-bold tracking-[0.15em] uppercase sm:text-[10px] sm:tracking-[0.2em]">
                    {currentQuestion.category}
                  </p>
                  <h3 className="font-amiri text-lg leading-snug font-bold tracking-tight text-ink uppercase italic sm:text-2xl sm:leading-tight">
                    {currentQuestion.text}
                  </h3>
                </div>
                <div className="relative flex h-14 w-14 shrink-0 items-center justify-center sm:h-16 sm:w-16">
                  <svg className="h-full w-full -rotate-90">
                    <circle 
                      cx="32" cy="32" r="28" 
                      fill="none" stroke="currentColor" strokeWidth="4" 
                      className="text-ink/15" 
                    />
                    <motion.circle 
                      cx="32" cy="32" r="28" 
                      fill="none" stroke="currentColor" strokeWidth="4" 
                      strokeDasharray="175.9"
                      animate={{
                        strokeDashoffset: 175.9 * (1 - duelTimerProgress),
                      }}
                      className="text-primary" 
                    />
                  </svg>
                  <span className="absolute text-lg font-black text-ink sm:text-xl">{timerSec}</span>
                </div>
              </div>

              {duelRole === 'spectate' ? (
                <div className="border-gold/30 space-y-4 rounded-xl border border-dashed bg-background/40 p-5 text-center">
                  <Shield className="text-primary mx-auto h-10 w-10 opacity-80" />
                  <p className="text-ink font-amiri text-lg font-bold">خصمك يخوض المبارزة على أرضٍ محايدة</p>
                  <p className="text-ink/65 text-sm leading-relaxed">
                    لن تُحسب إجابتك هنا — انتظر نتيجة الغزو. يمكنك متابعة العدّ التنازلي.
                  </p>
                </div>
              ) : (
                <>
                  {/* Options Grid */}
                  {audiencePct && audiencePct.length === currentQuestion.options.length ? (
                    <div className="border-gold/30 rounded-xl border bg-background/50 p-3">
                      <p className="text-primary mb-2 text-center font-mono text-[9px] font-bold uppercase">
                        استطلاع الجمهور (إرشاد تقريبي)
                      </p>
                      <div className="grid grid-cols-2 gap-2 text-xs text-ink">
                        {currentQuestion.options.map((opt, i) => (
                          <div key={i} className="flex justify-between gap-2">
                            <span className="truncate">{opt}</span>
                            <span className="font-mono text-primary">{audiencePct[i]}%</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  ) : null}

                  {spyglassIdx != null ? (
                    <p className="text-center font-mono text-xs text-amber-800">
                      المنظار: الخيار {spyglassIdx + 1} قد يكون الأقوى (ليست ضمانة).
                    </p>
                  ) : null}

                  {safetyOn ? (
                    <p className="text-center font-mono text-[10px] text-emerald-800">
                      درع الأمان — حماية رمزية لهذه الجولة
                    </p>
                  ) : null}

                  <div className="grid grid-cols-1 gap-2 sm:gap-3">
                    {currentQuestion.options.map((option, idx) => {
                      const eliminated = hiddenOptionIndices.includes(idx);
                      return (
                        <button
                          key={idx}
                          type="button"
                          onClick={() => handleAnswer(idx)}
                          disabled={selectedOption !== null || eliminated}
                          className={`group relative min-h-[48px] transform touch-manipulation overflow-hidden rounded-xl border p-4 text-right transition-all active:scale-[0.98] sm:min-h-0 sm:rounded-2xl sm:p-5 ${
                            eliminated
                              ? 'cursor-not-allowed border-ink/10 bg-ink/10 text-ink/30 line-through'
                              : selectedOption === idx
                                ? 'border-primary bg-primary text-background'
                                : 'border-gold/40 bg-background/80 text-ink hover:border-gold hover:bg-background'
                          }`}
                        >
                          <span className="relative z-10 text-base font-bold sm:text-lg">
                            {eliminated ? '—' : option}
                          </span>
                          <div className="absolute left-4 top-1/2 -translate-y-1/2 text-[10px] font-mono opacity-20 group-hover:opacity-40">
                            0{idx + 1}
                          </div>
                        </button>
                      );
                    })}
                  </div>

                  <div className="flex flex-wrap justify-center gap-1.5 sm:gap-2">
                    <button
                      type="button"
                      onClick={() => emitDuelPower('change_question')}
                      disabled={selectedOption !== null}
                      className="flex min-h-[44px] items-center gap-1 rounded-full border border-gold/40 px-3 py-2 text-[8px] font-bold uppercase text-ink touch-manipulation hover:bg-background sm:min-h-0 sm:text-[9px]"
                    >
                      <RotateCcw className="h-3 w-3" />
                      تبديل السؤال
                    </button>
                    <button
                      type="button"
                      onClick={() => emitDuelPower('audience')}
                      disabled={selectedOption !== null}
                      className="flex min-h-[44px] items-center gap-1 rounded-full border border-gold/40 px-3 py-2 text-[8px] font-bold uppercase text-ink touch-manipulation hover:bg-background sm:min-h-0 sm:text-[9px]"
                    >
                      <UsersRound className="h-3 w-3" />
                      الجمهور
                    </button>
                    <button
                      type="button"
                      onClick={() => emitDuelPower('safety')}
                      disabled={selectedOption !== null}
                      className="flex min-h-[44px] items-center gap-1 rounded-full border border-gold/40 px-3 py-2 text-[8px] font-bold uppercase text-ink touch-manipulation hover:bg-background sm:min-h-0 sm:text-[9px]"
                    >
                      <Lock className="h-3 w-3" />
                      أمان
                    </button>
                    <button
                      type="button"
                      onClick={handleDuelHammer}
                      disabled={duelHammerConsumed || selectedOption !== null}
                      className={`flex min-h-[44px] items-center gap-1 rounded-full border px-3 py-2 text-[8px] font-bold uppercase touch-manipulation transition-colors sm:min-h-0 sm:text-[9px] ${
                        duelHammerConsumed || selectedOption !== null
                          ? 'cursor-not-allowed border-ink/20 text-ink/35'
                          : 'border-primary/50 text-primary hover:bg-primary/10'
                      }`}
                    >
                      <Hammer className="h-3 w-3" />
                      50/50
                    </button>
                    <button
                      type="button"
                      onClick={() => emitDuelPower('spyglass')}
                      disabled={selectedOption !== null}
                      className="flex min-h-[44px] items-center gap-1 rounded-full border border-gold/40 px-3 py-2 text-[8px] font-bold uppercase text-ink touch-manipulation hover:bg-background sm:min-h-0 sm:text-[9px]"
                    >
                      <Eye className="h-3 w-3" />
                      منظار
                    </button>
                  </div>
                </>
              )}

              {/* Status */}
              <div className="text-ink/50 flex items-center justify-center gap-4 font-mono text-[10px] tracking-widest uppercase">
                <div className="flex items-center gap-2">
                  <Sword className="w-3 h-3" />
                  <span>{duelRole === 'spectate' ? 'مشاهدة المبارزة' : 'مبارزة جارية'}</span>
                </div>
                <div className="h-1 w-1 rounded-full bg-ink/25" />
                <div className="flex items-center gap-2">
                  <Timer className="w-3 h-3" />
                  <span>{Math.round(duelDurationMs / 1000)} ثوانٍ</span>
                </div>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>{duelOutcomeOverlay}</AnimatePresence>
    </div>
  );
}

function PowerUpButton({ icon, label, count, onClick }: { icon: React.ReactNode, label: string, count: number, onClick?: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="group flex shrink-0 touch-manipulation flex-col items-center gap-0.5 px-1 sm:gap-1 sm:px-0"
    >
      <div className="border-gold/30 group-hover:bg-background/15 relative flex min-h-[48px] min-w-[48px] items-center justify-center rounded-2xl border bg-background/30 p-2 transition-colors sm:min-h-0 sm:min-w-0 sm:p-3">
        {React.cloneElement(icon as React.ReactElement, { className: 'h-6 w-6 text-gold sm:h-5 sm:w-5' })}
        <span className="border-surface absolute -top-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full border-2 bg-primary text-[9px] font-black text-background sm:-top-2 sm:-right-2 sm:text-[10px]">
          {count}
        </span>
      </div>
      <span className="text-vellum max-w-[4.25rem] text-center text-[7px] font-bold leading-tight tracking-wide uppercase sm:max-w-none sm:text-[8px] sm:tracking-widest">
        {label}
      </span>
    </button>
  );
}
