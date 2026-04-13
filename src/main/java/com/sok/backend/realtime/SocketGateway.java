package com.sok.backend.realtime;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.domain.game.BattlePhaseService;
import com.sok.backend.domain.game.GameInputRules;
import com.sok.backend.domain.game.QuestionEngineService;
import com.sok.backend.domain.game.CastlePlacementPhaseService;
import com.sok.backend.domain.game.ClaimingPhaseService;
import com.sok.backend.domain.game.ResolutionPhaseService;
import com.sok.backend.config.RealtimeScaleProperties;
import com.sok.backend.service.AuthTokenService;
import com.sok.backend.service.ProgressionService;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.service.config.RuntimeGameConfigService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class SocketGateway implements DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(SocketGateway.class);
  private static final String PHASE_WAITING = "waiting";
  private static final String PHASE_CASTLE = "castle_placement";
  private static final String PHASE_CLAIM_Q = "claiming_question";
  private static final String PHASE_CLAIM_PICK = "claiming_pick";
  private static final String PHASE_BATTLE = "battle";
  private static final String PHASE_DUEL = "duel";
  private static final String PHASE_TIE = "battle_tiebreaker";
  private static final String PHASE_ENDED = "ended";

  private static final class MatchmakingAllocation {
    final String roomId;
    /** True if this JVM call created a new empty waiting room (safe to delete if join never runs). */
    final boolean brandNewEmpty;

    private MatchmakingAllocation(String roomId, boolean brandNewEmpty) {
      this.roomId = roomId;
      this.brandNewEmpty = brandNewEmpty;
    }
  }

  private static final List<String> PLAYER_COLORS =
      Arrays.asList("#C41E3A", "#228B22", "#1E90FF", "#9333EA", "#F59E0B", "#14B8A6", "#8B5CF6", "#EC4899");

  private final GameInputRules gameInputRules;
  private final QuestionEngineService questionEngineService;
  private final CastlePlacementPhaseService castlePlacementPhaseService;
  private final ClaimingPhaseService claimingPhaseService;
  private final BattlePhaseService battlePhaseService;
  private final ResolutionPhaseService resolutionPhaseService;
  private final ProgressionService progressionService;
  private final RuntimeGameConfigService runtimeConfigService;
  private final RealtimeScaleProperties scale;
  private final ObjectProvider<RoomRegistryService> roomRegistry;
  private final ObjectProvider<RoomSnapshotPublisher> snapshotPublisher;
  private final Counter rejectedRoomEvents;
  private final Counter rejectedRoomQueueFull;
  private final Timer roomTaskTimer;
  private final Map<String, RoomState> rooms = new ConcurrentHashMap<String, RoomState>();
  private final Map<String, String> uidToRoom = new ConcurrentHashMap<String, String>();
  private final Map<String, Object> roomLocks = new ConcurrentHashMap<String, Object>();
  private final Object matchmakingLock = new Object();
  private final ConcurrentHashMap<String, String> waitingInviteToRoomId = new ConcurrentHashMap<String, String>();
  private volatile String soloPublicWaitingRoomId;
  private final ThreadPoolExecutor roomWorkers;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
  private final AtomicLong roomSeq = new AtomicLong(1L);
  private final AtomicLong lastSnapshotTickMs = new AtomicLong(0L);

  public SocketGateway(
      GameInputRules gameInputRules,
      QuestionEngineService questionEngineService,
      CastlePlacementPhaseService castlePlacementPhaseService,
      ClaimingPhaseService claimingPhaseService,
      BattlePhaseService battlePhaseService,
      ResolutionPhaseService resolutionPhaseService,
      ProgressionService progressionService,
      RuntimeGameConfigService runtimeConfigService,
      RealtimeScaleProperties scale,
      ObjectProvider<RoomRegistryService> roomRegistry,
      ObjectProvider<RoomSnapshotPublisher> snapshotPublisher,
      MeterRegistry meterRegistry) {
    this.gameInputRules = gameInputRules;
    this.questionEngineService = questionEngineService;
    this.castlePlacementPhaseService = castlePlacementPhaseService;
    this.claimingPhaseService = claimingPhaseService;
    this.battlePhaseService = battlePhaseService;
    this.resolutionPhaseService = resolutionPhaseService;
    this.progressionService = progressionService;
    this.runtimeConfigService = runtimeConfigService;
    this.scale = scale;
    this.roomRegistry = roomRegistry;
    this.snapshotPublisher = snapshotPublisher;
    this.rejectedRoomEvents =
        Counter.builder("sok.realtime.rejected_events")
            .description("Events rejected due missing room or lock")
            .register(meterRegistry);
    this.rejectedRoomQueueFull =
        Counter.builder("sok.realtime.room_tasks_rejected_queue")
            .description("Room tasks rejected because worker pool queue is full")
            .register(meterRegistry);
    this.roomTaskTimer =
        Timer.builder("sok.realtime.room_task")
            .description("Latency of serialized room mutation tasks")
            .publishPercentileHistogram()
            .register(meterRegistry);
    this.roomWorkers =
        new ThreadPoolExecutor(
            scale.getRoomWorkerCoreThreads(),
            scale.getRoomWorkerMaxThreads(),
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(scale.getRoomTaskQueueCapacity()),
            new ThreadFactory() {
              private final AtomicLong n = new AtomicLong();

              @Override
              public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("room-worker-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
              }
            },
            new ThreadPoolExecutor.AbortPolicy());
    Gauge.builder("sok.realtime.rooms", rooms, new java.util.function.ToDoubleFunction<Map<String, RoomState>>() {
          @Override
          public double applyAsDouble(Map<String, RoomState> value) {
            return value.size();
          }
        })
        .register(meterRegistry);
    Gauge.builder("sok.realtime.players_online", this, new java.util.function.ToDoubleFunction<SocketGateway>() {
          @Override
          public double applyAsDouble(SocketGateway s) {
            return s.currentOnlinePlayers();
          }
        })
        .register(meterRegistry);
    Gauge.builder("sok.realtime.room_worker_queue", roomWorkers, SocketGateway::queueDepthAsDouble)
        .register(meterRegistry);
    Gauge.builder("sok.realtime.room_worker_active", roomWorkers, SocketGateway::activeCountAsDouble)
        .register(meterRegistry);
  }

  private static double queueDepthAsDouble(ThreadPoolExecutor p) {
    return p.getQueue().size();
  }

  private static double activeCountAsDouble(ThreadPoolExecutor p) {
    return p.getActiveCount();
  }

  public void register(
      SocketIOServer server, AuthTokenService authTokenService, boolean allowInsecureSocket) {
    server.addConnectListener(
        client -> {
          String token = client.getHandshakeData().getSingleUrlParam("token");
          try {
            String uid = authTokenService.verifyAccessToken(token).userId();
            client.set("uid", uid);
          } catch (Exception ex) {
            if (allowInsecureSocket) {
              String uid = client.getHandshakeData().getSingleUrlParam("uid");
              client.set(
                  "uid",
                  uid == null || uid.trim().isEmpty() ? "guest_" + client.getSessionId() : uid);
            } else {
              client.disconnect();
            }
          }
        });

    server.addEventListener(
        "join_matchmaking",
        JsonNode.class,
        (client, payload, ackRequest) -> {
          if (!payloadHasSocketUid(client, payload, "uid")) {
            client.disconnect();
            return;
          }
          String uid = asString(payload, "uid");
          String name = asString(payload, "name");
          if (name.trim().isEmpty()) name = "Warrior";
          final String finalName = name;
          String privateCode = payload.path("privateCode").asText("");
          String normalized = gameInputRules.normalizePrivateCode(privateCode);
          String existingRoomId = uidToRoom.get(uid);
          if (existingRoomId != null) {
            if (!submitToRoom(
                existingRoomId,
                new Runnable() {
                  @Override
                  public void run() {
                    RoomState room = rooms.get(existingRoomId);
                    if (room == null) return;
                    PlayerState p = room.playersByUid.get(uid);
                    if (p != null) {
                      p.socketId = client.getSessionId().toString();
                      p.online = true;
                      p.lastSeenAt = System.currentTimeMillis();
                    }
                    client.joinRoom(existingRoomId);
                    emitRoomUpdate(server, room);
                  }
                })) {
              client.sendEvent("join_rejected", mapOf("reason", "server_busy"));
            }
            return;
          }

          if (scale.getMaxOnlinePlayers() > 0
              && currentOnlinePlayers() >= scale.getMaxOnlinePlayers()) {
            client.sendEvent("join_rejected", mapOf("reason", "server_full"));
            return;
          }

          MatchmakingAllocation allocation = findOrCreateRoomAllocation(normalized);
          if (allocation == null) {
            client.sendEvent("join_rejected", mapOf("reason", "capacity"));
            return;
          }
          final String assignedRoomId = allocation.roomId;

          if (!submitToRoom(
              assignedRoomId,
              new Runnable() {
                @Override
                public void run() {
                  RoomState room = rooms.get(assignedRoomId);
                  if (room == null) return;
                  GameRuntimeConfig cfg = runtimeConfigService.get();
                  if (room.players.size() >= cfg.getMaxPlayers()) {
                    client.sendEvent("join_rejected", mapOf("reason", "room_full"));
                    return;
                  }
                  PlayerState player = new PlayerState();
                  player.uid = uid;
                  player.name = finalName;
                  player.socketId = client.getSessionId().toString();
                  player.online = true;
                  player.lastSeenAt = System.currentTimeMillis();
                  player.color = PLAYER_COLORS.get(room.players.size() % PLAYER_COLORS.size());
                  player.castleHp = cfg.getInitialCastleHp();
                  room.players.add(player);
                  room.playersByUid.put(uid, player);
                  uidToRoom.put(uid, room.id);
                  room.hostUid = room.players.get(0).uid;
                  client.joinRoom(room.id);
                  room.lastActivityAt = System.currentTimeMillis();
                  updateSoloPublicIndexAfterJoin(room);
                  emitRoomUpdate(server, room);
                  if (room.players.size() >= cfg.getMinPlayers() && PHASE_WAITING.equals(room.phase)) {
                    startCastlePlacementPhase(server, room);
                  }
                }
              })) {
            rollbackOrphanWaitingRoom(assignedRoomId, allocation.brandNewEmpty);
            client.sendEvent("join_rejected", mapOf("reason", "server_busy"));
          }
        });

    server.addEventListener(
        "leave_matchmaking",
        JsonNode.class,
        (client, payload, ack) -> {
          validateUidOrDisconnect(client, payload);
          String uid = asString(payload, "uid");
          if (uid.trim().isEmpty()) return;
          String roomId = uidToRoom.get(uid);
          if (roomId == null) return;
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = rooms.get(roomId);
              if (room == null) return;
              removePlayerFromRoom(server, room, uid);
            }
          });
        });

    server.addEventListener(
        "start_match",
        JsonNode.class,
        (client, payload, ack) -> {
          validateUidOrDisconnect(client, payload);
          String roomId = asString(payload, "roomId");
          String uid = asString(payload, "uid");
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = rooms.get(roomId);
              if (room == null || room.inviteCode == null || !PHASE_WAITING.equals(room.phase)) return;
              if (!uid.equals(room.hostUid)) return;
              GameRuntimeConfig cfg = runtimeConfigService.get();
              if (room.players.size() < cfg.getMinPlayers() || room.players.size() > cfg.getMaxPlayers()) return;
              startCastlePlacementPhase(server, room);
            }
          });
        });

    server.addEventListener(
        "place_castle",
        JsonNode.class,
        (client, payload, ack) -> {
          validateUidOrDisconnect(client, payload);
          String roomId = asString(payload, "roomId");
          String uid = asString(payload, "uid");
          int regionId = payload.path("regionId").asInt(-1);
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = rooms.get(roomId);
              if (room == null || !PHASE_CASTLE.equals(room.phase)) return;
              if (!room.regions.containsKey(regionId)) return;
              RegionState region = room.regions.get(regionId);
              if (region.ownerUid != null) return;
              PlayerState p = room.playersByUid.get(uid);
              if (p == null || p.castleRegionId != null) return;
              region.ownerUid = uid;
              region.isCastle = true;
              p.castleRegionId = regionId;
              p.score += pointValue(room, regionId);
              room.scoreByUid.put(uid, p.score);
              emitRoomUpdate(server, room);
              if (allPlayersPlacedCastle(room)) {
                startClaimingQuestionRound(server, room);
              }
            }
          });
        });

    server.addEventListener(
        "submit_estimation",
        JsonNode.class,
        (client, payload, ack) -> {
          validateUidOrDisconnect(client, payload);
          String roomId = asString(payload, "roomId");
          String uid = asString(payload, "uid");
          int value = payload.path("value").asInt(Integer.MIN_VALUE);
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = rooms.get(roomId);
              if (room == null) return;
              if (!PHASE_CLAIM_Q.equals(room.phase) && !PHASE_TIE.equals(room.phase)) return;
              if (value == Integer.MIN_VALUE) return;
              if (!room.playersByUid.containsKey(uid)) return;
              if (room.estimationAnswers.containsKey(uid)) return;
              AnswerMetric m = new AnswerMetric();
              m.uid = uid;
              m.value = value;
              m.latencyMs = System.currentTimeMillis() - room.phaseStartedAt;
              room.estimationAnswers.put(uid, m);
              if (room.estimationAnswers.size() == onlinePlayerCount(room)) {
                resolveEstimationRound(server, room);
              } else {
                emitRoomUpdate(server, room);
              }
            }
          });
        });

    server.addEventListener(
        "claim_region",
        JsonNode.class,
        (client, payload, ack) -> {
          validateUidOrDisconnect(client, payload);
          String roomId = asString(payload, "roomId");
          String uid = asString(payload, "uid");
          int regionId = payload.path("regionId").asInt(-1);
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = rooms.get(roomId);
              if (room == null || !PHASE_CLAIM_PICK.equals(room.phase)) return;
              if (!uid.equals(room.claimTurnUid)) return;
              Integer left = room.claimPicksLeftByUid.get(uid);
              if (left == null || left <= 0) return;
              RegionState r = room.regions.get(regionId);
              if (r == null || r.ownerUid != null) return;
              r.ownerUid = uid;
              PlayerState p = room.playersByUid.get(uid);
              p.score += pointValue(room, regionId);
              room.scoreByUid.put(uid, p.score);
              room.claimPicksLeftByUid.put(uid, left - 1);
              if (left - 1 <= 0) {
                rotateClaimTurn(room);
              }
              emitRoomUpdate(server, room);
              if (allRegionsClaimed(room)) {
                startBattlePhase(server, room);
              } else if (claimsQueueEmpty(room)) {
                startClaimingQuestionRound(server, room);
              }
            }
          });
        });

    server.addEventListener(
        "room_chat",
        JsonNode.class,
        (client, payload, ack) -> {
          validateUidOrDisconnect(client, payload);
          String roomId = asString(payload, "roomId");
          String uid = asString(payload, "uid");
          String name = asString(payload, "name");
          String message = gameInputRules.sanitizeChatMessage(asString(payload, "message"));
          if (message.trim().isEmpty()) return;
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = rooms.get(roomId);
              if (room == null || !room.playersByUid.containsKey(uid)) return;
              HashMap<String, Object> out = new HashMap<String, Object>();
              out.put("uid", uid);
              out.put("name", name.trim().isEmpty() ? "Player" : name);
              out.put("message", message);
              out.put("ts", System.currentTimeMillis());
              server.getRoomOperations(room.id).sendEvent("room_chat", out);
            }
          });
        });

    server.addEventListener(
        "attack",
        JsonNode.class,
        (client, payload, ack) -> {
          validateUidOrDisconnect(client, payload);
          String roomId = asString(payload, "roomId");
          String attackerUid = asString(payload, "attackerUid");
          int targetHexId = payload.path("targetHexId").asInt(-1);
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = rooms.get(roomId);
              if (room == null) {
                client.sendEvent("attack_invalid", mapOf("reason", "no_room"));
                return;
              }
              if (!PHASE_BATTLE.equals(room.phase)) {
                client.sendEvent("attack_invalid", mapOf("reason", "bad_phase"));
                return;
              }
              PlayerState turn = room.players.get(room.currentTurnIndex);
              if (!attackerUid.equals(turn.uid)) {
                client.sendEvent("attack_invalid", mapOf("reason", "not_your_turn"));
                return;
              }
              RegionState targetHex = room.regions.get(targetHexId);
              if (targetHex == null) {
                client.sendEvent("attack_invalid", mapOf("reason", "bad_hex"));
                return;
              }
              if (attackerUid.equals(targetHex.ownerUid)) {
                client.sendEvent("attack_invalid", mapOf("reason", "own_territory"));
                return;
              }
              if (!canAttackRegion(room, attackerUid, targetHexId)) {
                client.sendEvent("attack_invalid", mapOf("reason", "not_adjacent"));
                return;
              }
              startDuel(server, room, attackerUid, targetHexId, false);
            }
          });
        });

    server.addEventListener(
        "submit_answer",
        JsonNode.class,
        (client, payload, ack) -> {
          validateUidOrDisconnect(client, payload);
          String roomId = asString(payload, "roomId");
          String uid = asString(payload, "uid");
          Integer answerIndex =
              gameInputRules.coerceChoiceIndex(
                  payload.path("answerIndex").isMissingNode()
                      ? null
                      : payload.path("answerIndex").asText());
          if (answerIndex == null && payload.path("answerIndex").asInt(99999) == -1) {
            answerIndex = -1;
          }
          if (answerIndex == null) return;
          final int finalAnswer = answerIndex;
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = rooms.get(roomId);
              if (room == null || room.activeDuel == null || PHASE_TIE.equals(room.phase)) return;
              DuelState duel = room.activeDuel;
              boolean participant = uid.equals(duel.attackerUid) || uid.equals(duel.defenderUid);
              if (!participant || duel.answers.containsKey(uid)) return;
              DuelAnswer ans = new DuelAnswer();
              ans.answerIndex = finalAnswer;
              ans.timeTaken =
                  Math.min(
                      runtimeConfigService.get().getDuelDurationMs(),
                      System.currentTimeMillis() - room.phaseStartedAt);
              duel.answers.put(uid, ans);
              boolean done = duel.answers.containsKey(duel.attackerUid);
              if (!"neutral".equals(duel.defenderUid)) done = done && duel.answers.containsKey(duel.defenderUid);
              if (done) resolveDuel(server, room);
              else emitRoomUpdate(server, room);
            }
          });
        });

    server.addDisconnectListener(
        client -> {
          Object uidObj = client.get("uid");
          if (uidObj == null) return;
          String uid = String.valueOf(uidObj);
          String roomId = uidToRoom.get(uid);
          if (roomId == null) return;
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = rooms.get(roomId);
              if (room == null) return;
              PlayerState p = room.playersByUid.get(uid);
              if (p == null) return;
              p.online = false;
              p.lastSeenAt = System.currentTimeMillis();
              scheduleCleanup(server, room);
              emitRoomUpdate(server, room);
            }
          });
        });

    scheduler.scheduleAtFixedRate(
        new Runnable() {
          @Override
          public void run() {
            RoomRegistryService reg = roomRegistry.getIfAvailable();
            if (reg != null) {
              for (RoomState room : rooms.values()) {
                reg.refresh(room.id);
              }
            }
            maybePublishRoomSnapshots();
            for (RoomState room : rooms.values()) {
              submitToRoom(
                  room.id,
                  new Runnable() {
                    @Override
                    public void run() {
                      evaluateEndConditions(server, room);
                      evictDisconnectedPlayers(server, room);
                    }
                  });
            }
          }
        },
        5,
        5,
        TimeUnit.SECONDS);
  }

  private void validateUidOrDisconnect(SocketIOClient client, JsonNode payload) {
    if (!payloadHasSocketUid(client, payload, "uid") && !payloadHasSocketUid(client, payload, "attackerUid")) {
      client.disconnect();
    }
  }

  private boolean payloadHasSocketUid(SocketIOClient client, JsonNode payload, String field) {
    Object socketUid = client.get("uid");
    if (socketUid == null || payload == null || payload.path(field).isMissingNode()) {
      return false;
    }
    return payload.path(field).asText("").equals(String.valueOf(socketUid));
  }

  private String asString(JsonNode payload, String field) {
    if (payload == null || payload.path(field).isMissingNode()) {
      return "";
    }
    return payload.path(field).asText("");
  }

  private MatchmakingAllocation findOrCreateRoomAllocation(String normalizedInvite) {
    GameRuntimeConfig cfg = runtimeConfigService.get();
    synchronized (matchmakingLock) {
      if (scale.getMaxRooms() > 0 && rooms.size() >= scale.getMaxRooms()) {
        return null;
      }
      if (normalizedInvite != null && normalizedInvite.length() >= 4) {
        String idByInvite = waitingInviteToRoomId.get(normalizedInvite);
        if (idByInvite != null) {
          RoomState existing = rooms.get(idByInvite);
          if (existing != null
              && PHASE_WAITING.equals(existing.phase)
              && normalizedInvite.equals(existing.inviteCode)
              && existing.players.size() < cfg.getMaxPlayers()) {
            return new MatchmakingAllocation(idByInvite, false);
          }
          waitingInviteToRoomId.remove(normalizedInvite, idByInvite);
        }
        String id = newRoomId();
        RoomState room = new RoomState();
        room.id = id;
        room.phase = PHASE_WAITING;
        room.inviteCode = normalizedInvite;
        room.regions = buildRegionsFromConfig(cfg);
        room.currentTurnIndex = 0;
        room.createdAt = System.currentTimeMillis();
        room.lastActivityAt = room.createdAt;
        rooms.put(id, room);
        waitingInviteToRoomId.put(normalizedInvite, id);
        touchRoomRegistry(id);
        return new MatchmakingAllocation(id, true);
      }

      if (soloPublicWaitingRoomId != null) {
        RoomState solo = rooms.get(soloPublicWaitingRoomId);
        if (solo != null
            && PHASE_WAITING.equals(solo.phase)
            && solo.inviteCode == null
            && solo.players.size() == 1
            && solo.players.size() < cfg.getMaxPlayers()) {
          return new MatchmakingAllocation(soloPublicWaitingRoomId, false);
        }
        soloPublicWaitingRoomId = null;
      }

      for (RoomState room : rooms.values()) {
        if (PHASE_WAITING.equals(room.phase)
            && room.inviteCode == null
            && room.players.size() == 1
            && room.players.size() < cfg.getMaxPlayers()) {
          soloPublicWaitingRoomId = room.id;
          return new MatchmakingAllocation(room.id, false);
        }
      }

      String id = newRoomId();
      RoomState room = new RoomState();
      room.id = id;
      room.phase = PHASE_WAITING;
      room.inviteCode = null;
      room.regions = buildRegionsFromConfig(cfg);
      room.currentTurnIndex = 0;
      room.createdAt = System.currentTimeMillis();
      room.lastActivityAt = room.createdAt;
      rooms.put(id, room);
      soloPublicWaitingRoomId = id;
      touchRoomRegistry(id);
      return new MatchmakingAllocation(id, true);
    }
  }

  private void touchRoomRegistry(String roomId) {
    RoomRegistryService reg = roomRegistry.getIfAvailable();
    if (reg != null) {
      reg.refresh(roomId);
    }
  }

  private void maybePublishRoomSnapshots() {
    RoomSnapshotPublisher snap = snapshotPublisher.getIfAvailable();
    if (snap == null || !scale.isSnapshotToRedis()) {
      return;
    }
    long now = System.currentTimeMillis();
    long interval = scale.getSnapshotIntervalMs();
    if (interval <= 0L) {
      return;
    }
    long prev = lastSnapshotTickMs.get();
    if (now - prev < interval) {
      return;
    }
    if (!lastSnapshotTickMs.compareAndSet(prev, now)) {
      return;
    }
    for (RoomState room : rooms.values()) {
      snap.publish(room.id, minimalSnapshotJson(room));
    }
  }

  private static String minimalSnapshotJson(RoomState room) {
    return "{\"id\":\""
        + room.id
        + "\",\"phase\":\""
        + room.phase
        + "\",\"players\":"
        + room.players.size()
        + ",\"round\":"
        + room.round
        + "}";
  }

  private void rollbackOrphanWaitingRoom(String roomId, boolean brandNewEmpty) {
    if (!brandNewEmpty) {
      return;
    }
    RoomState r = rooms.get(roomId);
    if (r != null && r.players.isEmpty()) {
      shutdownRoom(roomId);
    }
  }

  private void updateSoloPublicIndexAfterJoin(RoomState room) {
    if (room.inviteCode != null) {
      return;
    }
    synchronized (matchmakingLock) {
      if (soloPublicWaitingRoomId != null
          && soloPublicWaitingRoomId.equals(room.id)
          && room.players.size() != 1) {
        soloPublicWaitingRoomId = null;
      }
    }
  }

  private void clearMatchmakingIndexesForRoom(RoomState room) {
    if (room == null) {
      return;
    }
    synchronized (matchmakingLock) {
      if (room.inviteCode != null) {
        waitingInviteToRoomId.remove(room.inviteCode, room.id);
      }
      if (room.id.equals(soloPublicWaitingRoomId)) {
        soloPublicWaitingRoomId = null;
      }
    }
  }

  private String newRoomId() {
    return "room_" + roomSeq.getAndIncrement();
  }

  private boolean submitToRoom(String roomId, Runnable task) {
    if (roomId == null) {
      rejectedRoomEvents.increment();
      return false;
    }
    final Object lock = roomLocks.computeIfAbsent(roomId, k -> new Object());
    Runnable wrapped =
        new Runnable() {
          @Override
          public void run() {
            try {
              if (scale.isRecordRoomTaskLatency()) {
                roomTaskTimer.record(
                    new Runnable() {
                      @Override
                      public void run() {
                        synchronized (lock) {
                          task.run();
                        }
                      }
                    });
              } else {
                synchronized (lock) {
                  task.run();
                }
              }
            } catch (RuntimeException ex) {
              log.debug("room task failed", ex);
            }
          }
        };
    try {
      roomWorkers.execute(wrapped);
      return true;
    } catch (RejectedExecutionException ex) {
      rejectedRoomQueueFull.increment();
      return false;
    }
  }

  public int roomCount() {
    return rooms.size();
  }

  /** Active worker threads processing room mutations (approximate). */
  public int roomExecutorCount() {
    return roomWorkers.getActiveCount();
  }

  public int roomWorkerQueueDepth() {
    return roomWorkers.getQueue().size();
  }

  public boolean isHealthy() {
    int cap = scale.getRoomTaskQueueCapacity();
    if (cap <= 0) {
      return true;
    }
    return roomWorkers.getQueue().size() < cap - 1;
  }

  public int currentOnlinePlayers() {
    int c = 0;
    for (RoomState r : rooms.values()) {
      for (PlayerState p : r.players) {
        if (p.online && !p.isEliminated) c++;
      }
    }
    return c;
  }

  private void removePlayerFromRoom(SocketIOServer server, RoomState room, String uid) {
    PlayerState removed = room.playersByUid.remove(uid);
    if (removed == null) {
      return;
    }
    uidToRoom.remove(uid);
    List<PlayerState> next = new ArrayList<PlayerState>();
    for (PlayerState p : room.players) {
      if (!uid.equals(p.uid)) {
        next.add(p);
      }
    }
    room.players = next;
    if (room.players.isEmpty()) {
      shutdownRoom(room.id);
      return;
    }
    room.hostUid = room.players.get(0).uid;
    if (room.players.size() == 1
        && PHASE_WAITING.equals(room.phase)
        && room.inviteCode == null) {
      synchronized (matchmakingLock) {
        soloPublicWaitingRoomId = room.id;
      }
    }
    emitRoomUpdate(server, room);
  }

  private void startCastlePlacementPhase(SocketIOServer server, RoomState room) {
    clearMatchmakingIndexesForRoom(room);
    GameRuntimeConfig cfg = runtimeConfigService.get();
    room.phase = PHASE_CASTLE;
    room.round = 1;
    room.currentTurnIndex = 0;
    room.matchStartedAt = System.currentTimeMillis();
    room.lastActivityAt = room.matchStartedAt;
    room.scoreByUid.clear();
    for (PlayerState p : room.players) {
      p.castleHp = cfg.getInitialCastleHp();
      p.castleRegionId = null;
      p.isEliminated = false;
      p.score = 0;
      room.scoreByUid.put(p.uid, 0);
    }
    HashMap<String, Object> payload = new HashMap<String, Object>();
    payload.put("phase", PHASE_CASTLE);
    payload.put("initialCastleHp", cfg.getInitialCastleHp());
    server.getRoomOperations(room.id).sendEvent("phase_changed", payload);
    emitRoomUpdate(server, room);
  }

  private Map<Integer, RegionState> buildRegionsFromConfig(GameRuntimeConfig cfg) {
    HashMap<Integer, RegionState> out = new HashMap<Integer, RegionState>();
    for (Map.Entry<String, List<Integer>> e : cfg.getNeighbors().entrySet()) {
      int id = Integer.parseInt(e.getKey());
      RegionState r = new RegionState();
      r.id = id;
      r.ownerUid = null;
      r.isCastle = false;
      r.points = cfg.getRegionPoints().containsKey(e.getKey()) ? cfg.getRegionPoints().get(e.getKey()) : 1;
      r.neighbors = new ArrayList<Integer>(e.getValue());
      out.put(id, r);
    }
    return out;
  }

  private void startClaimingQuestionRound(SocketIOServer server, RoomState room) {
    room.phase = PHASE_CLAIM_Q;
    room.phaseStartedAt = System.currentTimeMillis();
    room.claimPicksLeftByUid.clear();
    room.claimQueue.clear();
    room.estimationAnswers.clear();
    room.activeNumericQuestion = questionEngineService.nextNumericQuestion();
    GameRuntimeConfig cfg = runtimeConfigService.get();
    server
        .getRoomOperations(room.id)
        .sendEvent(
            "estimation_question",
            questionEngineService.toClient(
                room.activeNumericQuestion, room.phaseStartedAt, cfg.getClaimDurationMs()));
    scheduleTimer(
        room,
        "claim_question_timeout",
        cfg.getClaimDurationMs() + 50L,
        new Runnable() {
          @Override
          public void run() {
            resolveEstimationRound(server, room);
          }
        });
    emitRoomUpdate(server, room);
  }

  private void resolveEstimationRound(SocketIOServer server, RoomState room) {
    cancelTimer(room, "claim_question_timeout");
    if (room.activeNumericQuestion == null) return;
    if (room.estimationAnswers.isEmpty()) {
      for (PlayerState p : room.players) {
        if (!p.isEliminated && p.online) {
          AnswerMetric m = new AnswerMetric();
          m.uid = p.uid;
          m.value = 0;
          m.latencyMs = runtimeConfigService.get().getClaimDurationMs();
          room.estimationAnswers.put(p.uid, m);
        }
      }
    }
    List<ClaimingPhaseService.Metric> rows = new ArrayList<ClaimingPhaseService.Metric>();
    for (AnswerMetric metric : room.estimationAnswers.values()) {
      ClaimingPhaseService.Metric m = new ClaimingPhaseService.Metric();
      m.uid = metric.uid;
      m.value = metric.value;
      m.latencyMs = metric.latencyMs;
      rows.add(m);
    }
    List<ClaimingPhaseService.Metric> rankedRows =
        claimingPhaseService.rankByDeltaThenLatency(rows, room.activeNumericQuestion.answer);
    List<AnswerMetric> ranked = new ArrayList<AnswerMetric>();
    for (ClaimingPhaseService.Metric m : rankedRows) {
      AnswerMetric out = new AnswerMetric();
      out.uid = m.uid;
      out.value = m.value;
      out.latencyMs = m.latencyMs;
      ranked.add(out);
    }
    GameRuntimeConfig cfg = runtimeConfigService.get();
    room.claimPicksLeftByUid.clear();
    room.claimPicksLeftByUid.putAll(
        claimingPhaseService.assignClaimPicks(rankedRows, cfg.getClaimFirstPicks(), cfg.getClaimSecondPicks()));
    room.claimQueue.clear();
    for (AnswerMetric m : ranked) {
      Integer left = room.claimPicksLeftByUid.get(m.uid);
      if (left != null && left > 0) room.claimQueue.add(m.uid);
    }
    room.claimTurnUid = room.claimQueue.isEmpty() ? null : room.claimQueue.get(0);
    room.phase = PHASE_CLAIM_PICK;
    room.phaseStartedAt = System.currentTimeMillis();
    HashMap<String, Object> rankings = new HashMap<String, Object>();
    rankings.put("rankings", rankedToPayload(ranked, room.activeNumericQuestion.answer));
    rankings.put("claimPicks", room.claimPicksLeftByUid);
    server.getRoomOperations(room.id).sendEvent("claim_rankings", rankings);
    emitRoomUpdate(server, room);
  }

  private List<Map<String, Object>> rankedToPayload(List<AnswerMetric> ranked, int correctAnswer) {
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < ranked.size(); i++) {
      AnswerMetric m = ranked.get(i);
      HashMap<String, Object> row = new HashMap<String, Object>();
      row.put("uid", m.uid);
      row.put("rank", i + 1);
      row.put("delta", Math.abs(m.value - correctAnswer));
      row.put("latencyMs", m.latencyMs);
      out.add(row);
    }
    return out;
  }

  private void rotateClaimTurn(RoomState room) {
    if (room.claimQueue.isEmpty()) {
      room.claimTurnUid = null;
      return;
    }
    String current = room.claimQueue.remove(0);
    Integer remain = room.claimPicksLeftByUid.get(current);
    if (remain != null && remain > 0) {
      room.claimQueue.add(current);
    }
    room.claimTurnUid = room.claimQueue.isEmpty() ? null : room.claimQueue.get(0);
  }

  private boolean claimsQueueEmpty(RoomState room) {
    return room.claimTurnUid == null || room.claimQueue.isEmpty();
  }

  private boolean allRegionsClaimed(RoomState room) {
    for (RegionState r : room.regions.values()) {
      if (r.ownerUid == null) return false;
    }
    return true;
  }

  private boolean allPlayersPlacedCastle(RoomState room) {
    List<String> active = new ArrayList<String>();
    Map<String, Integer> castles = new HashMap<String, Integer>();
    for (PlayerState p : room.players) {
      if (p.isEliminated) continue;
      active.add(p.uid);
      castles.put(p.uid, p.castleRegionId);
    }
    return castlePlacementPhaseService.allCastlesPlaced(active, castles);
  }

  private void startBattlePhase(SocketIOServer server, RoomState room) {
    room.phase = PHASE_BATTLE;
    room.currentTurnIndex = firstAliveIndex(room);
    room.phaseStartedAt = System.currentTimeMillis();
    HashMap<String, Object> payload = new HashMap<String, Object>();
    payload.put("phase", PHASE_BATTLE);
    payload.put("round", room.round);
    server.getRoomOperations(room.id).sendEvent("phase_changed", payload);
    emitRoomUpdate(server, room);
  }

  private int firstAliveIndex(RoomState room) {
    for (int i = 0; i < room.players.size(); i++) {
      if (!room.players.get(i).isEliminated) return i;
    }
    return 0;
  }

  private boolean canAttackRegion(RoomState room, String attackerUid, int targetRegionId) {
    RegionState target = room.regions.get(targetRegionId);
    if (target == null) return false;
    for (Integer n : target.neighbors) {
      RegionState near = room.regions.get(n);
      if (near != null && attackerUid.equals(near.ownerUid)) return true;
    }
    return false;
  }

  private void startDuel(
      SocketIOServer server, RoomState room, String attackerUid, int targetRegionId, boolean tieBreakerAttack) {
    RegionState target = room.regions.get(targetRegionId);
    if (target == null) return;
    GameRuntimeConfig cfg = runtimeConfigService.get();
    room.phase = tieBreakerAttack ? PHASE_TIE : PHASE_DUEL;
    room.phaseStartedAt = System.currentTimeMillis();
    DuelState duel = new DuelState();
    duel.attackerUid = attackerUid;
    duel.defenderUid = target.ownerUid == null ? "neutral" : target.ownerUid;
    duel.targetRegionId = targetRegionId;
    if (tieBreakerAttack) {
      duel.numericQuestion = questionEngineService.nextNumericQuestion();
      server
          .getRoomOperations(room.id)
          .sendEvent(
              "battle_tiebreaker_start",
              questionEngineService.toClient(
                  duel.numericQuestion, room.phaseStartedAt, cfg.getTiebreakDurationMs()));
      scheduleTimer(
          room,
          "tiebreak_timeout",
          cfg.getTiebreakDurationMs() + 50L,
          new Runnable() {
            @Override
            public void run() {
              resolveTiebreaker(server, room);
            }
          });
    } else {
      duel.mcqQuestion = questionEngineService.nextMcqQuestion(cfg.getDefaultQuestionCategory());
      server
          .getRoomOperations(room.id)
          .sendEvent(
              "duel_start",
              mcqDuelPayload(duel, room.phaseStartedAt, cfg.getDuelDurationMs()));
      scheduleTimer(
          room,
          "duel_timeout",
          cfg.getDuelDurationMs() + 50L,
          new Runnable() {
            @Override
            public void run() {
              autoFillDuel(duel, cfg.getDuelDurationMs());
              resolveDuel(server, room);
            }
          });
    }
    room.activeDuel = duel;
    emitRoomUpdate(server, room);
  }

  private Map<String, Object> mcqDuelPayload(DuelState duel, long now, int durationMs) {
    HashMap<String, Object> payload = new HashMap<String, Object>();
    payload.put("question", questionEngineService.toClient(duel.mcqQuestion, now, durationMs));
    payload.put("serverNowMs", now);
    payload.put("phaseEndsAt", now + durationMs);
    payload.put("duelDurationMs", durationMs);
    payload.put("hiddenOptionIndices", new ArrayList<Integer>());
    payload.put("duelHammerConsumed", false);
    payload.put("attackerUid", duel.attackerUid);
    payload.put("defenderUid", duel.defenderUid);
    payload.put("targetHexId", duel.targetRegionId);
    return payload;
  }

  private void resolveDuel(SocketIOServer server, RoomState room) {
    cancelTimer(room, "duel_timeout");
    if (room.activeDuel == null || room.activeDuel.mcqQuestion == null) return;
    DuelState duel = room.activeDuel;
    DuelAnswer attacker = duel.answers.get(duel.attackerUid);
    DuelAnswer defender = "neutral".equals(duel.defenderUid) ? null : duel.answers.get(duel.defenderUid);
    boolean attackerCorrect = attacker != null && attacker.answerIndex == duel.mcqQuestion.correctIndex;
    boolean defenderCorrect =
        "neutral".equals(duel.defenderUid) ? false : defender != null && defender.answerIndex == duel.mcqQuestion.correctIndex;

    if (!"neutral".equals(duel.defenderUid)
        && attackerCorrect
        && defenderCorrect
        && attacker.timeTaken == defender.timeTaken) {
      startDuel(server, room, duel.attackerUid, duel.targetRegionId, true);
      return;
    }

    long a = attacker == null ? runtimeConfigService.get().getDuelDurationMs() : attacker.timeTaken;
    long d = defender == null ? runtimeConfigService.get().getDuelDurationMs() : defender.timeTaken;
    BattlePhaseService.DuelOutcome outcome =
        battlePhaseService.resolveMcq(
            attackerCorrect,
            defenderCorrect,
            a,
            d,
            !"neutral".equals(duel.defenderUid));
    boolean attackerWins = outcome == BattlePhaseService.DuelOutcome.ATTACKER_WINS;
    finishBattle(server, room, attackerWins, attackerCorrect, defenderCorrect, false, duel);
  }

  private void resolveTiebreaker(SocketIOServer server, RoomState room) {
    cancelTimer(room, "tiebreak_timeout");
    if (room.activeDuel == null || room.activeDuel.numericQuestion == null) return;
    DuelState duel = room.activeDuel;
    autoFillTiebreaker(duel, runtimeConfigService.get().getTiebreakDurationMs());
    AnswerMetric a = duel.tiebreakerAnswers.get(duel.attackerUid);
    AnswerMetric d = duel.tiebreakerAnswers.get(duel.defenderUid);
    if (d == null && "neutral".equals(duel.defenderUid)) {
      finishBattle(server, room, true, true, false, true, duel);
      return;
    }
    int ad = Math.abs(a.value - duel.numericQuestion.answer);
    int dd = Math.abs(d.value - duel.numericQuestion.answer);
    boolean attackerWins;
    if (ad != dd) attackerWins = ad < dd;
    else attackerWins = a.latencyMs <= d.latencyMs;
    finishBattle(server, room, attackerWins, true, true, true, duel);
  }

  private void finishBattle(
      SocketIOServer server,
      RoomState room,
      boolean attackerWins,
      boolean attackerCorrect,
      boolean defenderCorrect,
      boolean tieBreakerMinigame,
      DuelState duel) {
    if (duel == null) {
      return;
    }
    if (attackerWins) {
      applyAttackerCapture(room, duel);
    }
    room.phase = PHASE_BATTLE;
    room.activeDuel = null;
    advanceTurnSkipEliminated(room);
    room.roundAttackCount++;
    if (room.roundAttackCount >= room.players.size()) {
      room.roundAttackCount = 0;
      room.round++;
    }

    HashMap<String, Object> result = new HashMap<String, Object>();
    result.put("tieBreakerMinigame", tieBreakerMinigame);
    result.put("attackerWins", attackerWins);
    result.put("winnerUid", attackerWins ? duel.attackerUid : duel.defenderUid);
    result.put("attackerUid", duel.attackerUid);
    result.put("defenderUid", duel.defenderUid);
    result.put("attackerCorrect", attackerCorrect);
    result.put("defenderCorrect", defenderCorrect);
    result.put("wonBySpeed", attackerCorrect && defenderCorrect);
    result.put("correctIndex", duel.mcqQuestion != null ? duel.mcqQuestion.correctIndex : null);
    result.put("targetHexId", duel.targetRegionId);

    HashMap<String, Object> payload = new HashMap<String, Object>();
    payload.put("room", roomToClient(room));
    payload.put("result", result);
    server.getRoomOperations(room.id).sendEvent("duel_resolved", payload);
    emitRoomUpdate(server, room);
    evaluateEndConditions(server, room);
  }

  private void applyAttackerCapture(RoomState room, DuelState duel) {
    PlayerState attacker = room.playersByUid.get(duel.attackerUid);
    PlayerState defender = room.playersByUid.get(duel.defenderUid);
    RegionState hex = room.regions.get(duel.targetRegionId);
    if (attacker == null || hex == null) {
      return;
    }
    if (hex.isCastle && defender != null) {
      defender.castleHp = defender.castleHp - 1;
      if (defender.castleHp <= 0) {
        defender.isEliminated = true;
        defender.eliminatedAt = System.currentTimeMillis();
        for (RegionState h : room.regions.values()) {
          if (defender.uid.equals(h.ownerUid)) {
            h.ownerUid = attacker.uid;
            attacker.score += pointValue(room, h.id);
          }
        }
        room.scoreByUid.put(attacker.uid, attacker.score);
      }
      return;
    }
    hex.ownerUid = attacker.uid;
    hex.type = "player";
  }

  private void evaluateEndConditions(SocketIOServer server, RoomState room) {
    if (PHASE_ENDED.equals(room.phase)) return;
    List<PlayerState> alive = new ArrayList<PlayerState>();
    for (PlayerState p : room.players) {
      if (!p.isEliminated) {
        alive.add(p);
      }
    }
    if (alive.size() == 1) {
      finishGame(server, room, alive.get(0).uid, "domination");
      return;
    }
    GameRuntimeConfig cfg = runtimeConfigService.get();
    long elapsedSec = (System.currentTimeMillis() - room.matchStartedAt) / 1000L;
    if (room.round >= cfg.getMaxRounds() || elapsedSec >= cfg.getMaxMatchDurationSeconds()) {
      String winnerUid = topScorer(room);
      finishGame(server, room, winnerUid, "threshold");
    }
  }

  private void finishGame(SocketIOServer server, RoomState room, String winnerUid, String reason) {
    room.phase = PHASE_ENDED;
    HashMap<String, Object> payload = new HashMap<String, Object>();
    payload.put("winnerUid", winnerUid);
    payload.put("reason", reason);
    payload.put("rankings", rankings(room, winnerUid));
    payload.put("room", roomToClient(room));
    server.getRoomOperations(room.id).sendEvent("game_ended", payload);
    int place = 1;
    for (Map<String, Object> row : rankings(room, winnerUid)) {
      String uid = String.valueOf(row.get("uid"));
      int p = row.get("place") instanceof Number ? ((Number) row.get("place")).intValue() : place;
      progressionService.grantMatchResult(uid, p, room.id + ":" + room.matchStartedAt);
      place++;
    }
    scheduleRoomShutdown(room.id, runtimeConfigService.get().getReconnectGraceSeconds());
  }

  private String topScorer(RoomState room) {
    String winner = resolutionPhaseService.topScorer(room.scoreByUid);
    return winner == null && !room.players.isEmpty() ? room.players.get(0).uid : winner;
  }

  private List<Map<String, Object>> rankings(RoomState room, String winnerUid) {
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    out.add(mapOf("uid", winnerUid, "place", 1));
    int place = 2;
    for (PlayerState p : room.players) {
      if (!winnerUid.equals(p.uid)) {
        out.add(mapOf("uid", p.uid, "place", place++));
      }
    }
    return out;
  }

  private void advanceTurnSkipEliminated(RoomState room) {
    int n = room.players.size();
    for (int i = 0; i < n; i++) {
      room.currentTurnIndex = (room.currentTurnIndex + 1) % n;
      if (!room.players.get(room.currentTurnIndex).isEliminated) {
        return;
      }
    }
  }

  private void autoFillDuel(DuelState duel, int duelDurationMs) {
    if (!duel.answers.containsKey(duel.attackerUid)) {
      DuelAnswer a = new DuelAnswer();
      a.answerIndex = -1;
      a.timeTaken = duelDurationMs;
      duel.answers.put(duel.attackerUid, a);
    }
    if (!"neutral".equals(duel.defenderUid) && !duel.answers.containsKey(duel.defenderUid)) {
      DuelAnswer d = new DuelAnswer();
      d.answerIndex = -1;
      d.timeTaken = duelDurationMs;
      duel.answers.put(duel.defenderUid, d);
    }
  }

  private void autoFillTiebreaker(DuelState duel, int durationMs) {
    if (!duel.tiebreakerAnswers.containsKey(duel.attackerUid)) {
      AnswerMetric a = new AnswerMetric();
      a.uid = duel.attackerUid;
      a.value = 0;
      a.latencyMs = durationMs;
      duel.tiebreakerAnswers.put(a.uid, a);
    }
    if (!"neutral".equals(duel.defenderUid) && !duel.tiebreakerAnswers.containsKey(duel.defenderUid)) {
      AnswerMetric d = new AnswerMetric();
      d.uid = duel.defenderUid;
      d.value = 0;
      d.latencyMs = durationMs;
      duel.tiebreakerAnswers.put(d.uid, d);
    }
  }

  private void scheduleTimer(RoomState room, String key, long delayMs, Runnable task) {
    cancelTimer(room, key);
    ScheduledFuture<?> future = scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    room.timers.put(key, future);
  }

  private void cancelTimer(RoomState room, String key) {
    ScheduledFuture<?> f = room.timers.remove(key);
    if (f != null) f.cancel(false);
  }

  private void scheduleCleanup(SocketIOServer server, RoomState room) {
    scheduleTimer(room, "disconnect_cleanup", runtimeConfigService.get().getReconnectGraceSeconds() * 1000L, new Runnable() {
      @Override
      public void run() {
        evictDisconnectedPlayers(server, room);
      }
    });
  }

  private void evictDisconnectedPlayers(SocketIOServer server, RoomState room) {
    long now = System.currentTimeMillis();
    int graceMs = runtimeConfigService.get().getReconnectGraceSeconds() * 1000;
    List<String> toRemove = new ArrayList<String>();
    for (PlayerState p : room.players) {
      if (!p.online && now - p.lastSeenAt >= graceMs) toRemove.add(p.uid);
    }
    for (String uid : toRemove) removePlayerFromRoom(server, room, uid);
  }

  private void scheduleRoomShutdown(String roomId, int seconds) {
    scheduler.schedule(new Runnable() {
      @Override
      public void run() {
        shutdownRoom(roomId);
      }
    }, seconds, TimeUnit.SECONDS);
  }

  private void shutdownRoom(String roomId) {
    RoomState room = rooms.remove(roomId);
    if (room != null) {
      for (String uid : room.playersByUid.keySet()) uidToRoom.remove(uid);
      for (ScheduledFuture<?> f : room.timers.values()) f.cancel(false);
      room.timers.clear();
      clearMatchmakingIndexesForRoom(room);
    }
    roomLocks.remove(roomId);
    RoomRegistryService reg = roomRegistry.getIfAvailable();
    if (reg != null) {
      reg.remove(roomId);
    }
    RoomSnapshotPublisher snap = snapshotPublisher.getIfAvailable();
    if (snap != null) {
      snap.remove(roomId);
    }
  }

  private void emitRoomUpdate(SocketIOServer server, RoomState room) {
    room.lastActivityAt = System.currentTimeMillis();
    server.getRoomOperations(room.id).sendEvent("room_update", roomToClient(room));
  }

  private Map<String, Object> roomToClient(RoomState room) {
    HashMap<String, Object> payload = new HashMap<String, Object>();
    payload.put("id", room.id);
    payload.put("phase", room.phase);
    payload.put("round", room.round);
    payload.put("currentTurnIndex", room.currentTurnIndex);
    payload.put("hostUid", room.hostUid);
    payload.put("inviteCode", room.inviteCode);
    payload.put("players", playersToClient(room.players));
    payload.put("mapState", regionsToClient(room.regions));
    payload.put("scoreByUid", room.scoreByUid);
    payload.put("claimTurnUid", room.claimTurnUid);
    payload.put("claimPicksLeftByUid", room.claimPicksLeftByUid);
    if (room.activeDuel != null) {
      HashMap<String, Object> duel = new HashMap<String, Object>();
      duel.put("attackerUid", room.activeDuel.attackerUid);
      duel.put("defenderUid", room.activeDuel.defenderUid);
      duel.put("targetHexId", room.activeDuel.targetRegionId);
      duel.put("question", room.activeDuel.mcqQuestion != null ? room.activeDuel.mcqQuestion.text : null);
      payload.put("activeDuel", duel);
    }
    return payload;
  }

  private List<Map<String, Object>> playersToClient(List<PlayerState> players) {
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    for (PlayerState p : players) {
      HashMap<String, Object> row = new HashMap<String, Object>();
      row.put("uid", p.uid);
      row.put("name", p.name);
      row.put("hp", p.castleHp);
      row.put("color", p.color);
      row.put("isCapitalLost", p.isEliminated);
      row.put("trophies", p.trophies);
      row.put("eliminatedAt", p.eliminatedAt);
      row.put("castleRegionId", p.castleRegionId);
      row.put("online", p.online);
      out.add(row);
    }
    return out;
  }

  private List<Map<String, Object>> regionsToClient(Map<Integer, RegionState> regions) {
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    List<Integer> ids = new ArrayList<Integer>(regions.keySet());
    Collections.sort(ids);
    for (Integer id : ids) {
      RegionState h = regions.get(id);
      HashMap<String, Object> row = new HashMap<String, Object>();
      row.put("id", h.id);
      row.put("ownerUid", h.ownerUid);
      row.put("isCapital", h.isCastle);
      row.put("isShielded", h.isShielded);
      row.put("type", h.type);
      row.put("points", h.points);
      out.add(row);
    }
    return out;
  }

  private static HashMap<String, Object> mapOf(String k1, Object v1) {
    HashMap<String, Object> map = new HashMap<String, Object>();
    map.put(k1, v1);
    return map;
  }

  private static HashMap<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
    HashMap<String, Object> map = new HashMap<String, Object>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }

  private int onlinePlayerCount(RoomState room) {
    int count = 0;
    for (PlayerState p : room.players) {
      if (!p.isEliminated && p.online) count++;
    }
    return Math.max(1, count);
  }

  private int pointValue(RoomState room, int regionId) {
    RegionState r = room.regions.get(regionId);
    if (r == null) return 1;
    return r.points <= 0 ? 1 : r.points;
  }

  private static class RoomState {
    String id;
    List<PlayerState> players = new ArrayList<PlayerState>();
    Map<String, PlayerState> playersByUid = new HashMap<String, PlayerState>();
    Map<Integer, RegionState> regions = new HashMap<Integer, RegionState>();
    String phase = PHASE_WAITING;
    int currentTurnIndex = 0;
    String hostUid;
    String inviteCode;
    int round = 1;
    int roundAttackCount = 0;
    DuelState activeDuel;
    long createdAt;
    long lastActivityAt;
    long matchStartedAt;
    long phaseStartedAt;
    Map<String, Integer> scoreByUid = new HashMap<String, Integer>();
    Map<String, Integer> claimPicksLeftByUid = new HashMap<String, Integer>();
    List<String> claimQueue = new ArrayList<String>();
    String claimTurnUid;
    Map<String, AnswerMetric> estimationAnswers = new HashMap<String, AnswerMetric>();
    QuestionEngineService.NumericQuestion activeNumericQuestion;
    Map<String, ScheduledFuture<?>> timers = new HashMap<String, ScheduledFuture<?>>();
  }

  private static class PlayerState {
    String uid;
    String name;
    String socketId;
    int castleHp;
    Integer castleRegionId;
    int score;
    String color;
    boolean isEliminated;
    boolean online;
    long lastSeenAt;
    int trophies;
    Long eliminatedAt;
  }

  private static class RegionState {
    int id;
    String ownerUid;
    boolean isCastle;
    boolean isShielded;
    String type;
    int points;
    List<Integer> neighbors = new ArrayList<Integer>();
  }

  private static class DuelState {
    String attackerUid;
    String defenderUid;
    int targetRegionId;
    QuestionEngineService.McqQuestion mcqQuestion;
    QuestionEngineService.NumericQuestion numericQuestion;
    Map<String, DuelAnswer> answers = new HashMap<String, DuelAnswer>();
    Map<String, AnswerMetric> tiebreakerAnswers = new HashMap<String, AnswerMetric>();
  }

  private static class DuelAnswer {
    int answerIndex;
    long timeTaken;
  }

  private static class AnswerMetric {
    String uid;
    int value;
    long latencyMs;
  }

  @Override
  public void destroy() {
    for (String roomId : new ArrayList<String>(rooms.keySet())) {
      shutdownRoom(roomId);
    }
    scheduler.shutdownNow();
    roomWorkers.shutdown();
    try {
      if (!roomWorkers.awaitTermination(15L, TimeUnit.SECONDS)) {
        roomWorkers.shutdownNow();
      }
    } catch (InterruptedException ex) {
      roomWorkers.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
