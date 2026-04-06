import React, { useCallback, useEffect, useRef, useState } from 'react';
import { motion } from 'motion/react';
import { io, Socket } from 'socket.io-client';
import { Loader2, X, Users, Globe, Zap } from 'lucide-react';
import { auth } from '../firebase';
import { getSocketUrl } from '../apiConfig';
import { MatchState } from '../types';
import Battlefield from './Battlefield';

interface GameSessionProps {
  onBack: () => void;
}

function normalizeInvite(raw: string): string {
  return raw.replace(/[^a-zA-Z0-9]/g, '').toUpperCase().slice(0, 8);
}

export default function GameSession({ onBack }: GameSessionProps) {
  const [phase, setPhase] = useState<'lobby' | 'battle'>('lobby');
  const [lobbyRoom, setLobbyRoom] = useState<MatchState | null>(null);
  const [battleMatch, setBattleMatch] = useState<MatchState | null>(null);
  const [playSocket, setPlaySocket] = useState<Socket | null>(null);
  const [status, setStatus] = useState('جاري الاتصال بالخادم...');
  const [timer, setTimer] = useState(0);
  const [inviteDraft, setInviteDraft] = useState('');
  const [appliedInvite, setAppliedInvite] = useState('');
  const socketRef = useRef<Socket | null>(null);
  const appliedInviteRef = useRef('');
  const shouldRejoinRef = useRef(false);

  const emitJoin = useCallback((socket: Socket) => {
    const u = auth.currentUser;
    if (!u || !socket.connected) return;
    const code = normalizeInvite(appliedInviteRef.current);
    socket.emit('join_matchmaking', {
      uid: u.uid,
      name: u.displayName || 'Warrior',
      privateCode: code.length >= 4 ? code : null,
    });
  }, []);

  useEffect(() => {
    appliedInviteRef.current = appliedInvite;
  }, [appliedInvite]);

  useEffect(() => {
    const s = io(getSocketUrl(), { reconnection: true, reconnectionDelay: 500 });
    socketRef.current = s;

    const MIN_P = 2;
    const MAX_P = 4;

    const goBattle = (room: MatchState) => {
      if (
        (room.phase === 'conquest' ||
          room.phase === 'expansion' ||
          room.phase === 'duel' ||
          room.phase === 'tiebreaker') &&
        room.players.length >= MIN_P
      ) {
        setBattleMatch(room);
        setPlaySocket(s);
        setPhase('battle');
        setStatus('المعركة!');
      }
    };

    const onRoomUpdate = (room: MatchState) => {
      setLobbyRoom(room);
      const inv = Boolean(room.inviteCode);
      if (room.players.length < MIN_P) {
        setStatus(
          inv
            ? `غرفة خاصة: ${room.players.length}/${MAX_P} — ابدأ عند 2–4 لاعبين`
            : `في انتظار لاعب آخر (${room.players.length}/${MIN_P})`
        );
      } else if (inv) {
        const host = room.hostUid === auth.currentUser?.uid;
        setStatus(
          host
            ? 'أنت المضيف — اضغط «بدء المباراة» عند الجاهزية'
            : `في الغرفة: ${room.players.length}/${MAX_P} — انتظر المضيف`
        );
      } else {
        setStatus('جاري بدء المعركة...');
      }
      goBattle(room);
    };

    const onGameStart = (room: MatchState) => {
      setLobbyRoom(room);
      goBattle(room);
    };

    s.on('room_update', onRoomUpdate);
    s.on('game_start', onGameStart);

    const onConnect = () => {
      if (shouldRejoinRef.current) {
        setStatus('إعادة الاتصال بالبحث...');
        emitJoin(s);
      } else {
        setStatus('اختر: طابور عام، أو رمز غرفة (4 أحرف) ثم تطبيق — دون ذلك لن تُدخل مباراة.');
      }
    };
    s.on('connect', onConnect);
    if (s.connected) {
      onConnect();
    }

    const tick = setInterval(() => setTimer((t) => t + 1), 1000);

    return () => {
      clearInterval(tick);
      s.off('room_update', onRoomUpdate);
      s.off('game_start', onGameStart);
      s.off('connect', onConnect);
      s.disconnect();
      socketRef.current = null;
    };
  }, [emitJoin]);

  const handleLobbyBack = () => {
    shouldRejoinRef.current = false;
    const u = auth.currentUser;
    const s = socketRef.current;
    if (u && s?.connected) {
      s.emit('leave_matchmaking', { uid: u.uid });
    }
    s?.disconnect();
    onBack();
  };

  const startPublicQueue = () => {
    const u = auth.currentUser;
    const s = socketRef.current;
    if (!u || !s?.connected) return;
    shouldRejoinRef.current = true;
    setAppliedInvite('');
    appliedInviteRef.current = '';
    setInviteDraft('');
    s.emit('leave_matchmaking', { uid: u.uid });
    s.emit('join_matchmaking', {
      uid: u.uid,
      name: u.displayName || 'Warrior',
      privateCode: null,
    });
    setStatus('البحث في الطابور العام...');
  };

  const applyInviteCode = () => {
    const u = auth.currentUser;
    const s = socketRef.current;
    if (!u || !s?.connected) return;
    const next = normalizeInvite(inviteDraft);
    if (next.length > 0 && next.length < 4) {
      setStatus('رمز الغرفة يجب أن يكون 4 أحرفاً على الأقل (أو اترك الحقل فارغاً واستخدم الطابور العام).');
      return;
    }
    shouldRejoinRef.current = true;
    setAppliedInvite(next);
    appliedInviteRef.current = next;
    s.emit('leave_matchmaking', { uid: u.uid });
    s.emit('join_matchmaking', {
      uid: u.uid,
      name: u.displayName || 'Warrior',
      privateCode: next.length >= 4 ? next : null,
    });
    setStatus(next.length >= 4 ? `البحث عن غرفة: ${next}` : 'البحث في الطابور العام...');
  };

  const handleBattleEnd = () => {
    setPlaySocket(null);
    socketRef.current?.disconnect();
    onBack();
  };

  if (phase === 'battle' && battleMatch && playSocket?.connected) {
    return (
      <div className="flex min-h-0 flex-1 flex-col lg:min-h-[min(100dvh,900px)]">
        <Battlefield onEnd={handleBattleEnd} gameSocket={playSocket} initialMatch={battleMatch} />
      </div>
    );
  }

  if (phase === 'battle' && battleMatch && playSocket && !playSocket.connected) {
    return (
      <div className="text-ink/80 flex h-full flex-col items-center justify-center gap-4 p-6">
        <p className="text-sm font-bold">انقطع الاتصال. ارجع للغرفة وأعد المحاولة.</p>
        <button
          type="button"
          onClick={handleLobbyBack}
          className="text-primary font-bold uppercase underline text-xs"
        >
          العودة
        </button>
      </div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="flex h-full w-full flex-col items-center justify-center space-y-8 p-8 text-ink"
    >
      <div className="wood-shelf relative w-full max-w-lg overflow-hidden rounded-t-lg px-6 py-4 text-background shadow-heavy">
        <div className="absolute inset-0 bg-parchment opacity-10" />
        <div className="relative z-10 text-center">
          <h2 className="font-amiri text-2xl font-bold tracking-tight md:text-3xl">
            إعداد <span className="text-gold">المباراة</span>
          </h2>
          <p className="mt-1 font-mono text-[10px] text-background/85 uppercase tracking-widest">
            لن تُطابق مع خصم حتى تضغط «طابور عام» أو «تطبيق» برمز صالح
          </p>
        </div>
      </div>

      <button
        type="button"
        onClick={startPublicQueue}
        className="btn-wax btn-crimson vintage-border w-full max-w-sm rounded-lg py-4 font-black uppercase tracking-widest"
      >
        الدخول للطابور العام (يبدأ عند لاعبين)
      </button>

      <div className="vintage-border shadow-heavy relative w-full max-w-sm space-y-8 overflow-hidden rounded-lg bg-background/90 p-8 backdrop-blur-sm">
        <div className="absolute inset-0 bg-parchment opacity-40" />

        <div className="relative flex h-48 items-center justify-center">
          <motion.div
            animate={{ rotate: 360 }}
            transition={{ duration: 10, repeat: Infinity, ease: 'linear' }}
            className="border-gold/30 absolute inset-0 rounded-full border-2 border-dashed"
          />
          <div className="relative flex flex-col items-center gap-2">
            <Loader2 className="text-primary h-12 w-12 animate-spin" />
            <span className="font-mono text-lg font-bold text-ink">{timer}s</span>
          </div>
        </div>

        <div className="relative z-10 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[0, 1, 2, 3].map((i) => {
            const player = lobbyRoom?.players[i];
            return (
              <div key={i} className="flex flex-col items-center gap-2">
                <div
                  className={`flex h-14 w-14 items-center justify-center rounded-2xl border-2 transition-all sm:h-16 sm:w-16 ${
                    player ? 'border-gold bg-primary/10' : 'border-ink/15 bg-ink/5'
                  }`}
                  style={player ? { boxShadow: `0 0 0 2px ${player.color}33` } : undefined}
                >
                  {player ? (
                    <Users className="text-primary h-7 w-7 sm:h-8 sm:w-8" />
                  ) : (
                    <div className="h-2 w-2 animate-pulse rounded-full bg-ink/25" />
                  )}
                </div>
                <span
                  className={`max-w-[72px] truncate text-[9px] font-bold tracking-widest uppercase ${
                    player ? 'text-ink' : 'text-ink/35'
                  }`}
                >
                  {player ? player.name : '—'}
                </span>
              </div>
            );
          })}
        </div>

        {lobbyRoom?.inviteCode &&
        lobbyRoom.phase === 'waiting' &&
        lobbyRoom.players.length >= 2 &&
        lobbyRoom.players.length <= 4 &&
        lobbyRoom.hostUid === auth.currentUser?.uid ? (
          <button
            type="button"
            onClick={() => {
              const u = auth.currentUser;
              const sk = socketRef.current;
              if (!u || !sk?.connected || !lobbyRoom?.id) return;
              sk.emit('start_match', { roomId: lobbyRoom.id, uid: u.uid });
            }}
            className="btn-wax btn-crimson vintage-border relative z-10 mx-auto w-full max-w-sm rounded-lg py-3 font-black uppercase tracking-widest"
          >
            بدء المباراة ({lobbyRoom.players.length} لاعبين)
          </button>
        ) : null}

        <div className="relative z-10 space-y-2">
          <label className="text-ink/50 block font-mono text-[10px] uppercase">
            غرفة خاصة — نفس الرمز لك ولخصمك (4 أحرف فأكثر)
          </label>
          <div className="flex gap-2">
            <input
              value={inviteDraft}
              onChange={(e) => setInviteDraft(e.target.value)}
              placeholder="مثال: ABCD12"
              className="border-gold/40 focus:border-gold flex-1 rounded-xl border bg-background px-3 py-2 font-mono text-sm text-ink"
            />
            <button
              type="button"
              onClick={applyInviteCode}
              className="border-gold text-primary shrink-0 rounded-xl border bg-primary/10 px-3 py-2 font-mono text-[10px] font-bold uppercase"
            >
              تطبيق
            </button>
          </div>
          {appliedInvite.length >= 4 ? (
            <p className="font-mono text-[9px] text-ink/50">الرمز النشط: {appliedInvite}</p>
          ) : null}
        </div>

        <div className="border-gold/30 relative z-10 flex items-center justify-between border-t pt-6">
          <div className="text-ink/50 flex items-center gap-2">
            <Globe className="h-3 w-3" />
            <span className="font-mono text-[10px] uppercase">طابور عام</span>
          </div>
          <div className="text-primary flex items-center gap-2">
            <Zap className="h-3 w-3" />
            <span className="font-mono text-[10px] uppercase">2–4 لاعبين</span>
          </div>
        </div>
      </div>

      <p className="text-primary animate-pulse px-4 text-center text-sm font-bold">{status}</p>

      <button
        type="button"
        onClick={handleLobbyBack}
        className="text-ink/50 hover:text-ink flex items-center gap-2 font-mono text-[10px] font-bold tracking-widest uppercase transition-colors"
      >
        <X className="h-4 w-4" />
        إلغاء والعودة
      </button>
    </motion.div>
  );
}
