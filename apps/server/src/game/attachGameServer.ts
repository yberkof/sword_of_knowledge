import path from "path";
import { fileURLToPath } from "url";
import express from "express";
import type { Express } from "express";
import type { Server } from "socket.io";
import {
  pickRandomQuestion,
  fetchUserTrophies,
  persistActiveRoom,
  deleteActiveRoomDoc,
  applyMatchSettlement,
  applyMatchResults,
} from "../../server/pgData.js";
import { getHexNeighbors, pickCapitalHexIds } from "../../server/mapGeometry.js";
import {
  TIEBREAKER_GAMES_UI,
  openTiebreakerVote,
  tiebreakerClientPayload,
  recordTiebreakerVote,
  startTiebreakerGame,
  recordMinefieldPlacement,
  recordMinefieldStep,
  recordRhythmSubmit,
  recordRpsSubmit,
  recordClosestSubmit,
} from "../../server/tieBreaker.js";
import * as GameConstants from "./constants.js";
import { roomToClient } from "./roomSerialize.js";
import { createRoomsMap } from "./roomsStore.js";
import { registerSocketConnectionRateLimit } from "../socket/ioMiddleware.js";
import { registerDisconnectHandler } from "../socket/handlers/disconnect.js";

const {
  normalizePrivateCode,
  sanitizeChatMessage,
  coerceChoiceIndex,
  DUEL_DURATION_MS,
  EXPANSION_ROUND_MS,
  MIN_PLAYERS,
  MAX_PLAYERS,
  PLAYER_COLORS,
  HEX_POINTS_NORMAL,
  HEX_POINTS_CAPITAL_TILE,
  ATTACKS_PER_BATTLE_ROUND,
  EXPANSION_MAX_ROUNDS,
  ESTIMATION_QUESTIONS,
} = GameConstants;

export function attachGameServer(io: Server, app: Express): void {
  if (process.env.NODE_ENV === "production") {
    const distPath =
      process.env.WEB_DIST_PATH ||
      path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "..", "..", "web", "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res, next) => {
      if (req.path.startsWith("/api")) return next();
      res.sendFile(path.join(distPath, "index.html"));
    });
  }
  const rooms = createRoomsMap();

  registerSocketConnectionRateLimit(io);

  function canAttackHex(room: any, attackerUid: string, targetHexId: number): boolean {
    const neigh = getHexNeighbors(targetHexId);
    return neigh.some((nid) => {
      const h = room.mapState.find((x: { id: number }) => x.id === nid);
      return h && h.ownerUid === attackerUid;
    });
  }

  function clearDuelTimer(room: { duelTimerId?: NodeJS.Timeout | null }) {
    if (room.duelTimerId) {
      clearTimeout(room.duelTimerId);
      room.duelTimerId = null;
    }
  }

  function clearTiebreakerPickTimer(room: { tiebreakerPickTimerId?: NodeJS.Timeout | null }) {
    if (room.tiebreakerPickTimerId) {
      clearTimeout(room.tiebreakerPickTimerId);
      room.tiebreakerPickTimerId = null;
    }
  }

  function clearExpansionTimer(room: { expansionTimerId?: NodeJS.Timeout | null }) {
    if (room.expansionTimerId) {
      clearTimeout(room.expansionTimerId);
      room.expansionTimerId = null;
    }
  }

  function ensureMatchScore(room: any) {
    if (!room.matchScore || typeof room.matchScore !== "object") room.matchScore = {};
    for (const p of room.players) {
      if (room.matchScore[p.uid] == null) room.matchScore[p.uid] = 0;
    }
  }

  function transferHexPoints(room: any, hex: any, fromUid: string | null, toUid: string) {
    ensureMatchScore(room);
    const pts = hex?.isCapital ? HEX_POINTS_CAPITAL_TILE : HEX_POINTS_NORMAL;
    if (!fromUid) {
      room.matchScore[toUid] = (room.matchScore[toUid] || 0) + pts;
      return;
    }
    room.matchScore[fromUid] = Math.max(0, (room.matchScore[fromUid] || 0) - pts);
    room.matchScore[toUid] = (room.matchScore[toUid] || 0) + pts;
  }

  function advanceTurnSkipEliminated(room: any) {
    const n = room.players.length;
    if (n === 0) return;
    for (let i = 0; i < n; i++) {
      room.currentTurnIndex = (room.currentTurnIndex + 1) % n;
      if (!room.players[room.currentTurnIndex]?.isCapitalLost) return;
    }
  }

  function pickQuotasForRank(nPlayers: number): number[] {
    const q = [2, 1, 0, 0];
    return q.slice(0, nPlayers);
  }

  function resolveExpansionRound(roomId: string) {
    const room = rooms.get(roomId);
    if (!room?.expansion || room.expansion.phase !== "question") return;
    const exp = room.expansion;
    const correct = Number(exp.correctAnswer);
    const active = room.players.filter((p: any) => !p.isCapitalLost);
    for (const p of active) {
      const uid = String(p.uid);
      if (!exp.answers[uid]) {
        exp.answers[uid] = { value: correct + 1e9, timeTaken: EXPANSION_ROUND_MS, submitOrder: 999 };
      }
    }
    const ranked = active
      .map((p: any) => {
        const a = exp.answers[String(p.uid)];
        const err = Math.abs(Number(a.value) - correct);
        return {
          uid: String(p.uid),
          err,
          timeTaken: Number(a.timeTaken ?? EXPANSION_ROUND_MS),
          order: Number(a.submitOrder ?? 999),
        };
      })
      .sort((x, y) => {
        if (x.err !== y.err) return x.err - y.err;
        if (x.timeTaken !== y.timeTaken) return x.timeTaken - y.timeTaken;
        return x.order - y.order;
      });
    const quotas = pickQuotasForRank(active.length);
    const pickQueue: string[] = [];
    for (let i = 0; i < ranked.length; i++) {
      const uid = ranked[i]!.uid;
      const c = quotas[i] ?? 0;
      for (let k = 0; k < c; k++) pickQueue.push(uid);
    }
    exp.phase = "pick";
    exp.pickQueue = pickQueue;
    exp.rankedUids = ranked.map((r) => r.uid);
    exp.lastRankings = ranked.map((r, idx) => ({
      uid: r.uid,
      rank: idx + 1,
      error: r.err,
    }));
    clearExpansionTimer(room);
    io.to(roomId).emit("expansion_pick_phase", {
      room: roomToClient(room),
      pickQueue,
      rankings: exp.lastRankings,
    });
    io.to(roomId).emit("room_update", roomToClient(room));
  }

  function startExpansionRound(roomId: string) {
    const room = rooms.get(roomId);
    if (!room) return;
    if (!room.expansion) {
      room.phase = "conquest";
      io.to(roomId).emit("game_start", roomToClient(room));
      io.to(roomId).emit("room_update", roomToClient(room));
      return;
    }
    const r = room.expansion.round;
    if (r > EXPANSION_MAX_ROUNDS) {
      clearExpansionTimer(room);
      delete room.expansion;
      room.phase = "conquest";
      room.battleRound = 1;
      room.attacksRemainingThisRound = ATTACKS_PER_BATTLE_ROUND;
      room.currentTurnIndex = 0;
      while (room.players[room.currentTurnIndex]?.isCapitalLost) {
        room.currentTurnIndex = (room.currentTurnIndex + 1) % room.players.length;
      }
      io.to(roomId).emit("expansion_complete", { room: roomToClient(room) });
      io.to(roomId).emit("game_start", roomToClient(room));
      io.to(roomId).emit("room_update", roomToClient(room));
      return;
    }
    const q = ESTIMATION_QUESTIONS[(r - 1) % ESTIMATION_QUESTIONS.length]!;
    room.expansion.answers = {};
    room.expansion.phase = "question";
    const serverNow = Date.now();
    const phaseEndsAt = serverNow + EXPANSION_ROUND_MS;
    room.expansion.phaseEndsAt = phaseEndsAt;
    room.expansion.roundStartedAt = serverNow;
    room.expansion.correctAnswer = q.answer;
    room.expansion.nextOrd = 0;
    clearExpansionTimer(room);
    room.expansionTimerId = setTimeout(() => {
      resolveExpansionRound(roomId);
    }, EXPANSION_ROUND_MS + 80);
    io.to(roomId).emit("expansion_round_start", {
      roomId,
      round: r,
      maxRounds: EXPANSION_MAX_ROUNDS,
      questionText: q.text,
      serverNowMs: serverNow,
      phaseEndsAt,
      durationMs: EXPANSION_ROUND_MS,
    });
    io.to(roomId).emit("room_update", roomToClient(room));
  }

  async function startGameInternal(roomId: string) {
    const room = rooms.get(roomId);
    if (!room || room.phase !== "waiting") return;
    const n = room.players.length;
    if (n < MIN_PLAYERS || n > MAX_PLAYERS) return;
    room.mapState = generateHexMap();
    const caps = pickCapitalHexIds(n);
    room.players.forEach((p: any, i: number) => {
      p.hp = 3;
      p.isCapitalLost = false;
      p.eliminatedAt = undefined;
      p.color = PLAYER_COLORS[i % PLAYER_COLORS.length];
      const hex = room.mapState.find((h: any) => h.id === caps[i]);
      if (hex) {
        hex.ownerUid = p.uid;
        hex.isCapital = true;
      }
    });
    room.matchScore = {};
    room.players.forEach((p: any) => {
      room.matchScore[p.uid] = 0;
    });
    room.battleRound = 1;
    room.attacksRemainingThisRound = ATTACKS_PER_BATTLE_ROUND;
    room.currentTurnIndex = 0;
    while (room.players[room.currentTurnIndex]?.isCapitalLost) {
      room.currentTurnIndex = (room.currentTurnIndex + 1) % room.players.length;
    }
    /** Default: conquest-first so every capture runs a duel (classic flow). Set ENABLE_GAME_EXPANSION=1 for estimation + free tile picks. */
    const expansionOn =
      process.env.ENABLE_GAME_EXPANSION === "1" ||
      process.env.ENABLE_GAME_EXPANSION === "true";
    if (expansionOn) {
      room.phase = "expansion";
      room.expansion = { round: 1 };
    } else {
      room.phase = "conquest";
      delete room.expansion;
    }
    try {
      await persistActiveRoom(roomId, room);
    } catch (e) {
      console.warn("persistActiveRoom:", e instanceof Error ? e.message : e);
    }
    if (expansionOn) {
      startExpansionRound(roomId);
    } else {
      io.to(roomId).emit("game_start", roomToClient(room));
      io.to(roomId).emit("room_update", roomToClient(room));
    }
  }

  function autoFillMissingAnswers(room: any) {
    const duel = room.activeDuel;
    if (!duel) return;
    const participants =
      duel.defenderUid === "neutral"
        ? [String(duel.attackerUid)]
        : [String(duel.attackerUid), String(duel.defenderUid)];
    const deadline = duel.phaseEndsAt ?? Date.now();
    let fillSeq = typeof duel.nextAnswerOrdinal === "number" ? duel.nextAnswerOrdinal : 0;
    for (const pUid of participants) {
      if (!duel.answers[pUid]) {
        duel.answers[pUid] = {
          answerIndex: -1,
          timeTaken: Math.min(
            DUEL_DURATION_MS,
            Math.max(0, deadline - (duel.duelStartedAt ?? deadline))
          ),
          submitOrder: 1_000_000 + fillSeq++,
        };
      }
    }
    duel.nextAnswerOrdinal = fillSeq;
  }

  function startTiebreakerFlow(roomId: string, room: any, prev: any) {
    clearDuelTimer(room);
    clearTiebreakerPickTimer(room);
    room.activeDuel = null;
    room.phase = "tiebreaker";
    openTiebreakerVote(room, {
      attackerUid: String(prev.attackerUid),
      defenderUid: String(prev.defenderUid),
      targetHexId: Number(prev.targetHexId),
    });
    io.to(roomId).emit("tiebreaker_started", {
      games: TIEBREAKER_GAMES_UI,
      tieBreaker: tiebreakerClientPayload(room.tieBreaker as Record<string, unknown>),
    });
    io.to(roomId).emit("room_update", roomToClient(room));
  }

  function scheduleTiebreakerPickToGame(roomId: string) {
    const room = rooms.get(roomId);
    if (!room) return;
    clearTiebreakerPickTimer(room);
    room.tiebreakerPickTimerId = setTimeout(() => {
      const r = rooms.get(roomId);
      if (!r?.tieBreaker || (r.tieBreaker as { step?: string }).step !== "pick_resolved") return;
      const gid = startTiebreakerGame(r);
      if (!gid) return;
      r.tiebreakerPickTimerId = null;
      io.to(roomId).emit("tiebreaker_game_start", {
        gameId: gid,
        tieBreaker: tiebreakerClientPayload(r.tieBreaker as Record<string, unknown>),
      });
      io.to(roomId).emit("room_update", roomToClient(r));
    }, 3200);
  }

  function checkGameEnd(roomId: string, room: any) {
    if (room.phase === "ended") return;
    const alive = room.players.filter((p: any) => !p.isCapitalLost);
    if (alive.length !== 1) return;
    room.phase = "ended";
    clearDuelTimer(room);
    clearExpansionTimer(room);
    clearTiebreakerPickTimer(room);
    room.activeDuel = null;
    delete room.tieBreaker;
    delete room.expansion;
    const winner = alive[0]!;
    const dead = room.players
      .filter((p: any) => p.isCapitalLost)
      .sort((a: any, b: any) => (b.eliminatedAt || 0) - (a.eliminatedAt || 0));
    const rankings: { uid: string; place: number }[] = [{ uid: winner.uid, place: 1 }];
    dead.forEach((p: any, i: number) => rankings.push({ uid: p.uid, place: i + 2 }));
    io.to(roomId).emit("game_ended", {
      winnerUid: winner.uid,
      rankings,
      room: roomToClient(room),
    });
    void applyMatchResults(rankings).catch((e) => console.warn("applyMatchResults:", e));
    void deleteActiveRoomDoc(roomId);
  }

  function applyDuelHexOutcome(
    room: any,
    duel: { attackerUid: string; defenderUid: string; targetHexId: number },
    attackerWins: boolean
  ) {
    if (!attackerWins) return;
    const attacker = room.players.find((p: any) => p.uid === duel.attackerUid);
    const defender = room.players.find((p: any) => p.uid === duel.defenderUid);
    if (!attacker) return;
    const hex = room.mapState.find((h: any) => h.id === duel.targetHexId);
    if (!hex) return;
    if (hex.isCapital && defender) {
      defender.hp -= 1;
      if (defender.hp <= 0) {
        defender.isCapitalLost = true;
        defender.eliminatedAt = Date.now();
        ensureMatchScore(room);
        room.matchScore[attacker.uid] =
          (room.matchScore[attacker.uid] || 0) + (room.matchScore[defender.uid] || 0);
        room.matchScore[defender.uid] = 0;
        room.mapState.forEach((h: any) => {
          if (h.ownerUid === defender.uid) h.ownerUid = attacker.uid;
        });
      }
    } else {
      const prev = hex.ownerUid;
      transferHexPoints(room, hex, prev, attacker.uid);
      hex.ownerUid = attacker.uid;
    }
  }

  function finishTiebreakerAndEmit(
    roomId: string,
    room: any,
    attackerWins: boolean,
    minigameId: string,
    extra?: Record<string, unknown>
  ) {
    clearDuelTimer(room);
    clearTiebreakerPickTimer(room);
    const tb = room.tieBreaker as Record<string, unknown> | null | undefined;
    if (!tb) return;
    const ctx = {
      attackerUid: String(tb.attackerUid),
      defenderUid: String(tb.defenderUid),
      targetHexId: Number(tb.targetHexId),
    };
    applyDuelHexOutcome(room, ctx, attackerWins);
    room.phase = "conquest";
    room.tieBreaker = null;
    delete room.tieBreaker;
    room.activeDuel = null;
    room.attacksRemainingThisRound = (room.attacksRemainingThisRound ?? ATTACKS_PER_BATTLE_ROUND) - 1;
    if (room.attacksRemainingThisRound <= 0) {
      room.battleRound = (room.battleRound ?? 1) + 1;
      room.attacksRemainingThisRound = ATTACKS_PER_BATTLE_ROUND;
    }
    advanceTurnSkipEliminated(room);
    const winnerUid = attackerWins ? ctx.attackerUid : ctx.defenderUid;
    const result: Record<string, unknown> = {
      tieBreakerMinigame: true,
      minigame: minigameId,
      attackerWins,
      winnerUid,
      attackerCorrect: true,
      defenderCorrect: true,
      attackerUid: ctx.attackerUid,
      defenderUid: ctx.defenderUid,
      wonBySpeed: false,
      attackerTimeMs: null,
      defenderTimeMs: null,
      correctIndex: null,
      correctAnswerText: "",
      ...(extra || {}),
    };
    io.to(roomId).emit("duel_resolved", { room: roomToClient(room), result });
    io.to(roomId).emit("room_update", roomToClient(room));
    checkGameEnd(roomId, room);
  }

  function resolveAndEmitDuel(roomId: string, room: any) {
    if (!room.activeDuel) return;
    clearDuelTimer(room);
    const active = room.activeDuel;
    const result = resolveDuel(room) as Record<string, unknown>;
    if (result.tieBreak === true) {
      startTiebreakerFlow(roomId, room, active);
      return;
    }
    room.phase = "conquest";
    room.attacksRemainingThisRound = (room.attacksRemainingThisRound ?? ATTACKS_PER_BATTLE_ROUND) - 1;
    if (room.attacksRemainingThisRound <= 0) {
      room.battleRound = (room.battleRound ?? 1) + 1;
      room.attacksRemainingThisRound = ATTACKS_PER_BATTLE_ROUND;
    }
    advanceTurnSkipEliminated(room);
    room.activeDuel = null;
    const ci = coerceChoiceIndex(active.correctIndex);
    result.correctIndex = ci;
    result.correctAnswerText =
      ci != null && active.question?.options ? active.question.options[ci] ?? "" : "";
    io.to(roomId).emit("duel_resolved", { room: roomToClient(room), result });
    io.to(roomId).emit("room_update", roomToClient(room));
    checkGameEnd(roomId, room);
  }

  io.on("connection", (socket) => {
    console.log("User connected:", socket.id);

    const socketDeps = { rooms, io, roomToClient };
    registerDisconnectHandler(socket, socketDeps);

    /** Remove uid from a waiting lobby (before re-joining with a new code, or cancel). */
    socket.on("leave_matchmaking", (payload: { uid?: string }) => {
      const uid = payload?.uid != null ? String(payload.uid) : "";
      if (!uid) return;
      for (const [roomId, room] of rooms.entries()) {
        const playerIndex = room.players.findIndex((p: any) => p.uid === uid);
        if (playerIndex === -1) continue;
        if (room.phase !== "waiting") continue;
        room.players.splice(playerIndex, 1);
        socket.leave(roomId);
        if (room.players.length === 0) {
          rooms.delete(roomId);
        } else {
          io.to(roomId).emit("room_update", roomToClient(room));
        }
        break;
      }
    });

    socket.on("join_matchmaking", async (payload: { uid?: string; name?: string; privateCode?: string | null }) => {
      const uid = payload?.uid != null ? String(payload.uid) : "";
      const name = payload?.name != null ? String(payload.name) : "Warrior";
      if (!uid) return;

      let myTrophies = 0;
      try {
        myTrophies = await fetchUserTrophies(uid);
      } catch (e) {
        console.warn("fetchUserTrophies (matchmaking continues):", e instanceof Error ? e.message : e);
      }
      const priv = normalizePrivateCode(String(payload?.privateCode ?? ""));

      let existingRoomId: string | null = null;
      for (const [id, room] of rooms.entries()) {
        const p = room.players.find((x: any) => x.uid === uid);
        if (p) {
          existingRoomId = id;
          p.socketId = socket.id;
          break;
        }
      }

      if (existingRoomId) {
        socket.join(existingRoomId);
        const r = rooms.get(existingRoomId);
        if (r) {
          io.to(existingRoomId).emit("room_update", roomToClient(r));
          if (r.phase === "conquest" || r.phase === "expansion") {
            io.to(existingRoomId).emit("game_start", roomToClient(r));
          }
        }
        return;
      }

      let roomId: string | null = null;

      if (priv.length >= 4) {
        for (const [id, room] of rooms.entries()) {
          if (
            room.inviteCode === priv &&
            room.phase === "waiting" &&
            room.players.length < MAX_PLAYERS
          ) {
            roomId = id;
            break;
          }
        }
        if (!roomId) {
          roomId = `room_${Date.now()}`;
          rooms.set(roomId, {
            id: roomId,
            players: [],
            phase: "waiting",
            mapState: generateHexMap(),
            currentTurnIndex: 0,
            inviteCode: priv,
          });
        }
      } else {
        let emptyRoom: string | null = null;
        for (const [id, room] of rooms.entries()) {
          if (
            !room.inviteCode &&
            room.phase === "waiting" &&
            room.players.length === 1
          ) {
            roomId = id;
            break;
          }
        }
        if (!roomId) {
          for (const [id, room] of rooms.entries()) {
            if (
              !room.inviteCode &&
              room.phase === "waiting" &&
              room.players.length === 0
            ) {
              emptyRoom = id;
              break;
            }
          }
          roomId = emptyRoom;
        }
        if (!roomId) {
          roomId = `room_${Date.now()}`;
          rooms.set(roomId, {
            id: roomId,
            players: [],
            phase: "waiting",
            mapState: generateHexMap(),
            currentTurnIndex: 0,
          });
        }
      }

      const room = rooms.get(roomId)!;
      if (room.players.length >= MAX_PLAYERS) {
        socket.emit("join_rejected", { reason: "room_full" });
        return;
      }
      const player = {
        socketId: socket.id,
        uid,
        name,
        trophies: myTrophies,
        hp: 3,
        color: PLAYER_COLORS[room.players.length % PLAYER_COLORS.length],
        isCapitalLost: false,
      };
      room.players.push(player);
      room.hostUid = room.players[0]?.uid;
      socket.join(roomId);

      const isPrivate = Boolean(room.inviteCode);
      if (!isPrivate && room.players.length >= MIN_PLAYERS) {
        await startGameInternal(roomId);
      } else {
        io.to(roomId).emit("room_update", roomToClient(room));
      }
    });

    socket.on("start_match", async (payload: { roomId?: string; uid?: string }) => {
      const roomId = String(payload?.roomId || "");
      const uid = payload?.uid != null ? String(payload.uid) : "";
      const room = rooms.get(roomId);
      if (!room || room.phase !== "waiting" || !room.inviteCode) return;
      if (!uid || uid !== String(room.players[0]?.uid)) return;
      if (room.players.length < MIN_PLAYERS || room.players.length > MAX_PLAYERS) return;
      await startGameInternal(roomId);
    });

    socket.on(
      "expansion_submit_number",
      (payload: { roomId?: string; uid?: string; value?: number }) => {
        const roomId = String(payload?.roomId || "");
        const uid = payload?.uid != null ? String(payload.uid) : "";
        const room = rooms.get(roomId);
        if (!room?.expansion || room.expansion.phase !== "question") return;
        if (!uid || room.expansion.answers[uid]) return;
        if (!room.players.some((p: any) => p.uid === uid)) return;
        const v =
          typeof payload?.value === "number" && Number.isFinite(payload.value)
            ? payload.value
            : parseFloat(String(payload?.value ?? ""));
        if (!Number.isFinite(v)) return;
        const started = Number(room.expansion.roundStartedAt) || Date.now();
        const elapsed = Math.min(EXPANSION_ROUND_MS, Math.max(0, Date.now() - started));
        const ord = room.expansion.nextOrd ?? 0;
        room.expansion.nextOrd = ord + 1;
        room.expansion.answers[uid] = { value: v, timeTaken: elapsed, submitOrder: ord };
        const active = room.players.filter((p: any) => !p.isCapitalLost);
        if (active.every((p: any) => room.expansion!.answers[String(p.uid)])) {
          clearExpansionTimer(room);
          resolveExpansionRound(roomId);
        } else {
          io.to(roomId).emit("room_update", roomToClient(room));
        }
      }
    );

    socket.on("expansion_pick_hex", (payload: { roomId?: string; uid?: string; hexId?: number }) => {
      const roomId = String(payload?.roomId || "");
      const uid = payload?.uid != null ? String(payload.uid) : "";
      const hexId = payload?.hexId;
      const room = rooms.get(roomId);
      if (!room?.expansion || room.expansion.phase !== "pick") {
        socket.emit("expansion_pick_invalid", { reason: "not_pick_phase" });
        return;
      }
      if (typeof hexId !== "number" || hexId < 0 || hexId > 12) {
        socket.emit("expansion_pick_invalid", { reason: "bad_hex" });
        return;
      }
      const pq = room.expansion.pickQueue;
      if (!pq?.length || pq[0] !== uid) {
        socket.emit("expansion_pick_invalid", { reason: "not_your_pick" });
        return;
      }
      const hex = room.mapState.find((h: any) => h.id === hexId);
      if (!hex || hex.ownerUid != null) {
        socket.emit("expansion_pick_invalid", { reason: "not_neutral" });
        return;
      }
      const touches = getHexNeighbors(hexId).some((nid) => {
        const h = room.mapState.find((x: any) => x.id === nid);
        return h && h.ownerUid === uid;
      });
      if (!touches) {
        socket.emit("expansion_pick_invalid", { reason: "not_adjacent" });
        return;
      }
      hex.ownerUid = uid;
      hex.isCapital = false;
      pq.shift();
      if (pq.length === 0) {
        room.expansion.round++;
        room.expansion.phase = "question";
        startExpansionRound(roomId);
      } else {
        io.to(roomId).emit("room_update", roomToClient(room));
      }
    });

    socket.on("room_chat", ({ roomId, uid, name, message }: { roomId?: string; uid?: string; name?: string; message?: string }) => {
      const room = rooms.get(String(roomId || ""));
      if (!room || room.phase === "ended") return;
      const uidStr = String(uid || "");
      if (!room.players.some((p: any) => p.uid === uidStr)) return;
      const text = sanitizeChatMessage(String(message || ""));
      if (!text) return;
      io.to(String(roomId)).emit("room_chat", {
        uid: String(uid || ""),
        name: String(name || "Player"),
        message: text,
        ts: Date.now(),
      });
    });

    socket.on("attack", async ({ roomId, attackerUid, targetHexId, category }) => {
      const room = rooms.get(roomId);
      if (!room) {
        socket.emit("attack_invalid", { reason: "no_room" });
        return;
      }
      if (room.phase !== "conquest") {
        socket.emit("attack_invalid", { reason: "bad_phase" });
        return;
      }
      if (room.players[room.currentTurnIndex]?.uid !== attackerUid) {
        socket.emit("attack_invalid", { reason: "not_your_turn" });
        return;
      }

      const targetHex = room.mapState.find(h => h.id === targetHexId);
      if (!targetHex) {
        socket.emit("attack_invalid", { reason: "bad_hex" });
        return;
      }

      if (targetHex.ownerUid === attackerUid) {
        socket.emit("attack_invalid", { reason: "own_territory" });
        return;
      }

      if (!canAttackHex(room, attackerUid, targetHexId)) {
        socket.emit("attack_invalid", { reason: "not_adjacent" });
        return;
      }

      // Check for Shield
      if (targetHex.isShielded) {
        targetHex.isShielded = false; // Shield consumed
        io.to(roomId).emit("room_update", roomToClient(room));
        io.to(roomId).emit("attack_blocked", { targetHexId });
        room.attacksRemainingThisRound =
          (room.attacksRemainingThisRound ?? ATTACKS_PER_BATTLE_ROUND) - 1;
        if (room.attacksRemainingThisRound <= 0) {
          room.battleRound = (room.battleRound ?? 1) + 1;
          room.attacksRemainingThisRound = ATTACKS_PER_BATTLE_ROUND;
        }
        advanceTurnSkipEliminated(room);
        io.to(roomId).emit("room_update", roomToClient(room));
        return;
      }

      const randomQ = await pickRandomQuestion(
        typeof category === "string" ? category : null
      );

      clearDuelTimer(room);
      const serverNow = Date.now();
      const phaseEndsAt = serverNow + DUEL_DURATION_MS;

      room.phase = "duel";
      room.activeDuel = {
        attackerUid,
        defenderUid: targetHex.ownerUid || "neutral",
        targetHexId,
        question: {
          id: randomQ.id,
          text: randomQ.text,
          options: randomQ.options,
          category: randomQ.category,
          difficulty: randomQ.difficulty,
        },
        correctIndex: coerceChoiceIndex(randomQ.correctIndex) ?? 0,
        isTieBreak: false,
        startTime: new Date().toISOString(),
        duelStartedAt: serverNow,
        phaseEndsAt,
        answers: {},
        hiddenOptionIndices: [],
        duelHammerConsumed: false,
        changeQuestionConsumed: false,
        audienceConsumed: false,
        safetyConsumed: false,
        spyglassConsumed: false,
        nextAnswerOrdinal: 0,
      };

      io.to(roomId).emit("duel_start", {
        question: room.activeDuel.question,
        serverNowMs: serverNow,
        phaseEndsAt,
        duelDurationMs: DUEL_DURATION_MS,
        hiddenOptionIndices: [],
        duelHammerConsumed: false,
        attackerUid,
        defenderUid: room.activeDuel.defenderUid,
        targetHexId,
      });
      io.to(roomId).emit("room_update", roomToClient(room));

      room.duelTimerId = setTimeout(() => {
        const r = rooms.get(roomId);
        if (!r?.activeDuel) return;
        autoFillMissingAnswers(r);
        resolveAndEmitDuel(roomId, r);
      }, DUEL_DURATION_MS + 30);
    });

    socket.on("submit_answer", ({ roomId, uid, answerIndex }) => {
      const room = rooms.get(roomId);
      if (!room || room.phase === "tiebreaker" || !room.activeDuel) return;
      const uidStr = uid != null ? String(uid) : "";
      if (!uidStr) return;

      const participants =
        room.activeDuel.defenderUid === "neutral"
          ? [String(room.activeDuel.attackerUid)]
          : [String(room.activeDuel.attackerUid), String(room.activeDuel.defenderUid)];
      if (!participants.includes(uidStr)) return;

      if (room.activeDuel.answers[uidStr]) return;

      const idx = coerceChoiceIndex(answerIndex);
      const normalizedIdx = idx !== null ? idx : answerIndex === -1 ? -1 : null;
      if (normalizedIdx === null) return;

      const start = room.activeDuel.duelStartedAt ?? Date.now();
      const elapsed = Math.min(DUEL_DURATION_MS, Math.max(0, Date.now() - start));

      if (typeof room.activeDuel.nextAnswerOrdinal !== "number") {
        room.activeDuel.nextAnswerOrdinal = 0;
      }
      const ord = room.activeDuel.nextAnswerOrdinal++;
      room.activeDuel.answers[uidStr] = {
        answerIndex: normalizedIdx,
        timeTaken: elapsed,
        submitOrder: ord,
      };

      if (participants.every((pUid) => room.activeDuel!.answers[pUid])) {
        resolveAndEmitDuel(roomId, room);
      }
    });

    socket.on(
      "tiebreaker_vote",
      (payload: { roomId?: string; uid?: string; gameId?: string }) => {
        const roomId = String(payload?.roomId || "");
        const room = rooms.get(roomId);
        if (!room || room.phase !== "tiebreaker" || !room.tieBreaker) return;
        const uidStr = payload?.uid != null ? String(payload.uid) : "";
        const r = recordTiebreakerVote(room, uidStr, String(payload?.gameId || ""));
        if (r.kind === "error") return;
        if (r.kind === "wait") {
          io.to(roomId).emit("room_update", roomToClient(room));
          return;
        }
        io.to(roomId).emit("tiebreaker_pick_result", {
          agreed: r.agreed,
          votes: r.votes,
          selected: r.selected,
          tieBreaker: tiebreakerClientPayload(room.tieBreaker as Record<string, unknown>),
        });
        io.to(roomId).emit("room_update", roomToClient(room));
        scheduleTiebreakerPickToGame(roomId);
      }
    );

    socket.on(
      "tiebreaker_minefield_place",
      (payload: { roomId?: string; uid?: unknown; cells?: unknown }) => {
        const roomId = String(payload?.roomId || "");
        const room = rooms.get(roomId);
        if (!room || room.phase !== "tiebreaker") return;
        const r = recordMinefieldPlacement(room, String(payload?.uid ?? ""), payload?.cells);
        if (r.kind === "error") return;
        io.to(roomId).emit("room_update", roomToClient(room));
        if (r.kind === "play_start") io.to(roomId).emit("tiebreaker_minefield_ready");
      }
    );

    socket.on(
      "tiebreaker_minefield_step",
      (payload: { roomId?: string; uid?: unknown; cell?: unknown }) => {
        const roomId = String(payload?.roomId || "");
        const room = rooms.get(roomId);
        if (!room || room.phase !== "tiebreaker") return;
        const r = recordMinefieldStep(room, String(payload?.uid ?? ""), payload?.cell);
        if (r.kind === "error") return;
        if (r.kind === "duel_done") {
          finishTiebreakerAndEmit(roomId, room, r.attackerWins, "minefield");
          return;
        }
        io.to(roomId).emit("room_update", roomToClient(room));
      }
    );

    socket.on(
      "tiebreaker_rhythm_submit",
      (payload: { roomId?: string; uid?: unknown; sequence?: unknown }) => {
        const roomId = String(payload?.roomId || "");
        const room = rooms.get(roomId);
        if (!room || room.phase !== "tiebreaker") return;
        const r = recordRhythmSubmit(room, String(payload?.uid ?? ""), payload?.sequence);
        if (r.kind === "error") {
          socket.emit("tiebreaker_rhythm_error", { code: r.message });
          return;
        }
        if (r.kind === "wait") {
          io.to(roomId).emit("room_update", roomToClient(room));
          return;
        }
        if (r.kind === "duel_done") {
          finishTiebreakerAndEmit(roomId, room, r.attackerWins, "rhythm");
          return;
        }
        if (r.kind === "next_round") {
          // room_update first so clients have tb.rhythmPattern / rhythmRound before replay nonce
          io.to(roomId).emit("room_update", roomToClient(room));
          io.to(roomId).emit("tiebreaker_rhythm_next", { pattern: r.pattern });
        }
      }
    );

    socket.on(
      "tiebreaker_rps_submit",
      (payload: { roomId?: string; uid?: unknown; pick?: unknown }) => {
        const roomId = String(payload?.roomId || "");
        const room = rooms.get(roomId);
        if (!room || room.phase !== "tiebreaker") return;
        const r = recordRpsSubmit(room, String(payload?.uid ?? ""), payload?.pick);
        if (r.kind === "error") return;
        if (r.kind === "wait") {
          io.to(roomId).emit("room_update", roomToClient(room));
          return;
        }
        if (r.kind === "duel_done") {
          const tbR = room.tieBreaker as Record<string, unknown> | undefined;
          const last = tbR?.rpsLast as
            | { attackerPick?: number; defenderPick?: number; roundWinner?: string }
            | undefined;
          const aU = String(tbR?.attackerUid ?? "");
          const dU = String(tbR?.defenderUid ?? "");
          if (
            last &&
            typeof last.attackerPick === "number" &&
            typeof last.defenderPick === "number"
          ) {
            io.to(roomId).emit("tiebreaker_rps_round", {
              picks: { [aU]: last.attackerPick, [dU]: last.defenderPick },
              roundWinner: last.roundWinner,
              matchComplete: true,
            });
          }
          io.to(roomId).emit("room_update", roomToClient(room));
          const win = r.attackerWins;
          setTimeout(() => {
            const r2 = rooms.get(roomId);
            if (!r2 || r2.phase !== "tiebreaker") return;
            finishTiebreakerAndEmit(roomId, r2, win, "rps");
          }, 3200);
          return;
        }
        if (r.kind === "round_done") {
          io.to(roomId).emit("tiebreaker_rps_round", {
            picks: r.picks,
            roundWinner: r.roundWinner,
            matchComplete: false,
          });
          io.to(roomId).emit("room_update", roomToClient(room));
        }
      }
    );

    socket.on(
      "tiebreaker_closest_submit",
      (payload: { roomId?: string; uid?: unknown; value?: unknown }) => {
        const roomId = String(payload?.roomId || "");
        const room = rooms.get(roomId);
        if (!room || room.phase !== "tiebreaker") return;
        const r = recordClosestSubmit(room, String(payload?.uid ?? ""), payload?.value);
        if (r.kind === "error") return;
        if (r.kind === "wait") {
          io.to(roomId).emit("room_update", roomToClient(room));
          return;
        }
        if (r.kind === "duel_done") {
          const tb0 = room.tieBreaker as Record<string, unknown> | undefined;
          const aU = String(tb0?.attackerUid ?? "");
          const dU = String(tb0?.defenderUid ?? "");
          const wU = r.attackerWins ? aU : dU;
          io.to(roomId).emit("tiebreaker_closest_reveal", {
            target: r.target,
            guesses: r.guesses,
            questionText: r.questionText,
            winnerUid: wU,
            attackerUid: aU,
            defenderUid: dU,
          });
          finishTiebreakerAndEmit(roomId, room, r.attackerWins, "closest", {
            closestTarget: r.target,
            closestGuesses: r.guesses,
            closestQuestionText: r.questionText,
          });
        }
      }
    );

    socket.on("use_powerup", async ({ roomId, uid, powerupType, targetHexId }) => {
      const room = rooms.get(roomId);
      if (!room) return;

      if (room.phase === "duel" && room.activeDuel && powerupType === "hammer") {
        const d = room.activeDuel;
        if (d.duelHammerConsumed) return;
        const opts = d.question?.options;
        const n = Array.isArray(opts) ? opts.length : 0;
        const ci = coerceChoiceIndex(d.correctIndex) ?? 0;
        if (n < 3) return;
        d.hiddenOptionIndices = pickTwoWrongOptionIndices(n, ci);
        d.duelHammerConsumed = true;
        io.to(roomId).emit("duel_options_update", {
          hiddenOptionIndices: d.hiddenOptionIndices,
          duelHammerConsumed: true,
        });
        return;
      }

      if (room.phase === "duel" && room.activeDuel && powerupType === "change_question") {
        const d = room.activeDuel;
        if (d.changeQuestionConsumed) return;
        const randomQ = await pickRandomQuestion(null);
        clearDuelTimer(room);
        d.changeQuestionConsumed = true;
        d.question = {
          id: randomQ.id,
          text: randomQ.text,
          options: randomQ.options,
          category: randomQ.category,
          difficulty: randomQ.difficulty,
        };
        d.correctIndex = coerceChoiceIndex(randomQ.correctIndex) ?? 0;
        d.answers = {};
        d.hiddenOptionIndices = [];
        d.duelHammerConsumed = false;
        d.nextAnswerOrdinal = 0;
        const serverNow = Date.now();
        d.duelStartedAt = serverNow;
        d.phaseEndsAt = serverNow + DUEL_DURATION_MS;
        room.duelTimerId = setTimeout(() => {
          const r = rooms.get(roomId);
          if (!r?.activeDuel) return;
          autoFillMissingAnswers(r);
          resolveAndEmitDuel(roomId, r);
        }, DUEL_DURATION_MS + 30);
        io.to(roomId).emit("duel_start", {
          question: d.question,
          serverNowMs: serverNow,
          phaseEndsAt: d.phaseEndsAt,
          duelDurationMs: DUEL_DURATION_MS,
          hiddenOptionIndices: [],
          duelHammerConsumed: false,
          attackerUid: d.attackerUid,
          defenderUid: d.defenderUid,
          targetHexId: d.targetHexId,
        });
        io.to(roomId).emit("room_update", roomToClient(room));
        return;
      }

      if (room.phase === "duel" && room.activeDuel && powerupType === "audience") {
        const d = room.activeDuel;
        if (d.audienceConsumed) return;
        const opts = d.question?.options;
        const n = Array.isArray(opts) ? opts.length : 0;
        if (n < 2) return;
        const ci = coerceChoiceIndex(d.correctIndex) ?? 0;
        const weights = Array.from({ length: n }, () => 8 + Math.random() * 22);
        weights[Math.min(ci, n - 1)] += 35;
        const sum = weights.reduce((a, b) => a + b, 0);
        const percentages = weights.map((w) => Math.round((1000 * w) / sum) / 10);
        d.audienceConsumed = true;
        io.to(roomId).emit("duel_audience_hint", { percentages });
        return;
      }

      if (room.phase === "duel" && room.activeDuel && powerupType === "safety") {
        const d = room.activeDuel;
        if (d.safetyConsumed) return;
        d.safetyConsumed = true;
        io.to(roomId).emit("duel_safety_locked", {});
        return;
      }

      if (room.phase === "duel" && room.activeDuel && powerupType === "spyglass") {
        const d = room.activeDuel;
        if (d.spyglassConsumed) return;
        const opts = d.question?.options;
        const n = Array.isArray(opts) ? opts.length : 0;
        if (n < 2) return;
        const ci = coerceChoiceIndex(d.correctIndex) ?? 0;
        const hintIdx =
          Math.random() < 0.42 ? Math.min(Math.max(0, ci), n - 1) : Math.floor(Math.random() * n);
        d.spyglassConsumed = true;
        io.to(roomId).emit("duel_spyglass_hint", { suggestedIndex: hintIdx });
        return;
      }

      if (room.phase !== "conquest") return;

      const player = room.players.find(p => p.uid === uid);
      if (!player || room.players[room.currentTurnIndex].uid !== uid) return;

      // In real app, check inventory in Postgres
      // For now, assume player has it

      if (powerupType === "hammer" && targetHexId !== undefined) {
        const hex = room.mapState.find(h => h.id === targetHexId);
        if (hex && !hex.isCapital) {
          transferHexPoints(room, hex, hex.ownerUid, uid);
          hex.ownerUid = uid;
          io.to(roomId).emit("room_update", roomToClient(room));
          room.attacksRemainingThisRound =
            (room.attacksRemainingThisRound ?? ATTACKS_PER_BATTLE_ROUND) - 1;
          if (room.attacksRemainingThisRound <= 0) {
            room.battleRound = (room.battleRound ?? 1) + 1;
            room.attacksRemainingThisRound = ATTACKS_PER_BATTLE_ROUND;
          }
          advanceTurnSkipEliminated(room);
          io.to(roomId).emit("room_update", roomToClient(room));
        }
      } else if (powerupType === "shield" && targetHexId !== undefined) {
        const hex = room.mapState.find(h => h.id === targetHexId);
        if (hex && hex.ownerUid === uid) {
          hex.isShielded = true;
          io.to(roomId).emit("room_update", roomToClient(room));
          // Shield doesn't end turn? Let's say no
        }
      } else if (powerupType === "spyglass") {
        // This is handled client-side mostly, but we can emit a hint
        // Actually, it's better to handle it during a duel
      }
    });
  });

  function generateHexMap() {
    const tiles = [];
    for (let i = 0; i < 13; i++) {
      tiles.push({
        id: i,
        ownerUid: null,
        isCapital: false,
        type: "neutral",
      });
    }
    return tiles;
  }

  function pickTwoWrongOptionIndices(numOptions: number, correctIndex: number): number[] {
    const wrong: number[] = [];
    for (let i = 0; i < numOptions; i++) {
      if (i !== correctIndex) wrong.push(i);
    }
    for (let i = wrong.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [wrong[i], wrong[j]] = [wrong[j]!, wrong[i]!];
    }
    return wrong.slice(0, Math.min(2, wrong.length));
  }

  function resolveDuel(room) {
    const duel = room.activeDuel;
    const attacker = room.players.find(p => p.uid === duel.attackerUid);
    const defender = room.players.find(p => p.uid === duel.defenderUid);

    const attackerAnswer = duel.answers[String(duel.attackerUid)];
    const defenderAnswer = defender ? duel.answers[String(duel.defenderUid)] : null;

    const correctIdx = coerceChoiceIndex(duel.correctIndex);
    if (correctIdx === null) {
      console.error("Invalid duel.correctIndex:", duel.correctIndex);
      return {
        tieBreak: false,
        attackerWins: false,
        attackerCorrect: false,
        defenderCorrect: false,
        attackerUid: duel.attackerUid,
        defenderUid: duel.defenderUid,
        winnerUid: null as string | null,
        attackerTimeMs: null as number | null,
        defenderTimeMs: null as number | null,
        wonBySpeed: false,
      };
    }
    const correct = correctIdx;
    const attackerIdx = coerceChoiceIndex(attackerAnswer?.answerIndex);
    const defenderIdx = coerceChoiceIndex(defenderAnswer?.answerIndex);
    /** -1 from timeout/wrong must not match correct */
    const attackerCorrect =
      attackerAnswer != null &&
      attackerIdx !== null &&
      attackerIdx >= 0 &&
      attackerIdx === correct;
    const defenderCorrect =
      defender != null &&
      defenderAnswer != null &&
      defenderIdx !== null &&
      defenderIdx >= 0 &&
      defenderIdx === correct;

    if (!attackerCorrect && !defenderCorrect && defender) {
      return {
        tieBreak: true,
        attackerWins: false,
        attackerCorrect,
        defenderCorrect,
        attackerUid: duel.attackerUid,
        defenderUid: duel.defenderUid,
        winnerUid: null,
        attackerTimeMs: Number(attackerAnswer?.timeTaken ?? 0),
        defenderTimeMs: Number(defenderAnswer?.timeTaken ?? 0),
        wonBySpeed: false,
      };
    }

    let attackerWins = false;
    if (attackerCorrect && !defenderCorrect) {
      attackerWins = true;
    } else if (!attackerCorrect && defenderCorrect) {
      attackerWins = false;
    } else if (attackerCorrect && defenderCorrect && defender) {
      /** Both right → always minigame tiebreaker (speed tie was too rare and felt unfair). */
      return {
        tieBreak: true,
        attackerWins: false,
        attackerCorrect,
        defenderCorrect,
        attackerUid: duel.attackerUid,
        defenderUid: duel.defenderUid,
        winnerUid: null,
        attackerTimeMs: Number(attackerAnswer?.timeTaken ?? 0),
        defenderTimeMs: Number(defenderAnswer?.timeTaken ?? 0),
        wonBySpeed: false,
      };
    } else {
      attackerWins = false;
    }

    applyDuelHexOutcome(
      room,
      {
        attackerUid: String(duel.attackerUid),
        defenderUid: String(duel.defenderUid),
        targetHexId: Number(duel.targetHexId),
      },
      attackerWins
    );

    let winnerUid: string | null = null;
    if (attackerWins) {
      winnerUid = String(duel.attackerUid);
    } else if (defender) {
      winnerUid = String(duel.defenderUid);
    }

    return {
      tieBreak: false,
      attackerWins,
      attackerCorrect,
      defenderCorrect,
      attackerUid: duel.attackerUid,
      defenderUid: duel.defenderUid,
      winnerUid,
      attackerTimeMs: attackerAnswer != null ? Number(attackerAnswer.timeTaken) : null,
      defenderTimeMs:
        defender && defenderAnswer != null ? Number(defenderAnswer.timeTaken) : null,
      wonBySpeed: false,
    };
  }
}
