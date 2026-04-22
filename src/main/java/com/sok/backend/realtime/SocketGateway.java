package com.sok.backend.realtime;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.sok.backend.domain.game.tiebreaker.XoTieBreakInteractionService;
import com.sok.backend.domain.game.tiebreaker.XoTieBreakPayloadFactory;
import com.sok.backend.domain.game.tiebreaker.AvoidBombsTieBreakInteractionService;
import com.sok.backend.domain.game.tiebreaker.AvoidBombsTieBreakPayloadFactory;
import com.sok.backend.domain.game.tiebreaker.AvoidBombsTieBreakerAttackPhaseStrategy;
import com.sok.backend.domain.game.GameInputRules;
import com.sok.backend.config.RealtimeScaleProperties;
import com.sok.backend.service.AuthTokenService;
import com.sok.backend.service.config.GameRuntimeConfig;
import com.sok.backend.realtime.match.AnswerMetric;
import com.sok.backend.realtime.match.BattleOrchestrator;
import com.sok.backend.realtime.match.CastlePlacementOrchestrator;
import com.sok.backend.realtime.match.ClaimPhaseOrchestrator;
import com.sok.backend.realtime.match.DuelAnswer;
import com.sok.backend.realtime.match.DuelState;
import com.sok.backend.realtime.match.MatchOutcomeService;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RegionState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.binding.SocketEventBinder;
import com.sok.backend.realtime.matchmaking.MatchmakingAllocator;
import com.sok.backend.persistence.ActiveRoomRepository;
import com.sok.backend.realtime.persistence.RoomRehydrationService;
import com.sok.backend.realtime.persistence.RoomSnapshotCoordinator;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomClientSnapshotFactory;
import com.sok.backend.realtime.room.RoomExecutorRegistry;
import com.sok.backend.realtime.room.RoomLifecycle;
import com.sok.backend.realtime.room.RoomRulesResolver;
import com.sok.backend.realtime.room.RoomStateFactory;
import com.sok.backend.realtime.room.RoomStore;
import com.sok.backend.realtime.room.RoomTimerScheduler;
import com.sok.backend.service.config.RuntimeGameConfigService;

import static com.sok.backend.realtime.room.RoomClientSnapshotFactory.mapOf;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private static final String PHASE_TIE = "battle_tiebreaker";

  private static final List<String> PLAYER_COLORS =
      Arrays.asList("#C41E3A", "#228B22", "#1E90FF", "#9333EA", "#F59E0B", "#14B8A6", "#8B5CF6", "#EC4899");

  private final GameInputRules gameInputRules;
  private final RuntimeGameConfigService runtimeConfigService;
  private final RealtimeScaleProperties scale;
  private final ObjectProvider<RoomRegistryService> roomRegistry;
  private final RoomSnapshotCoordinator snapshotCoordinator;
  private final RoomExecutorRegistry roomExecutors;
  private final RoomTimerScheduler roomTimers;
  private final RoomBroadcaster broadcaster;
  private final RoomStore store;
  private final RoomRulesResolver rulesResolver;
  private final RoomLifecycle lifecycle;
  private final MatchmakingAllocator matchmakingAllocator;
  private final AtomicLong lastSnapshotTickMs = new AtomicLong(0L);
  private final XoTieBreakInteractionService xoTieBreakInteractionService;
  private final AvoidBombsTieBreakInteractionService avoidBombsTieBreakInteractionService;
  private final RoomClientSnapshotFactory snapshotFactory;
  private final CastlePlacementOrchestrator castlePlacement;
  private final ClaimPhaseOrchestrator claimPhase;
  private final BattleOrchestrator battle;
  private final MatchOutcomeService matchOutcome;
  private final List<SocketEventBinder> eventBinders;
  private final ActiveRoomRepository activeRoomRepository;
  private final RoomRehydrationService roomRehydrationService;

  public SocketGateway(
      GameInputRules gameInputRules,
      RuntimeGameConfigService runtimeConfigService,
      XoTieBreakInteractionService xoTieBreakInteractionService,
      AvoidBombsTieBreakInteractionService avoidBombsTieBreakInteractionService,
      RealtimeScaleProperties scale,
      ObjectProvider<RoomRegistryService> roomRegistry,
      RoomSnapshotCoordinator snapshotCoordinator,
      MeterRegistry meterRegistry,
      RoomClientSnapshotFactory snapshotFactory,
      RoomExecutorRegistry roomExecutors,
      RoomTimerScheduler roomTimers,
      RoomBroadcaster broadcaster,
      RoomStore store,
      RoomRulesResolver rulesResolver,
      RoomLifecycle lifecycle,
      MatchmakingAllocator matchmakingAllocator,
      CastlePlacementOrchestrator castlePlacement,
      ClaimPhaseOrchestrator claimPhase,
      BattleOrchestrator battle,
      MatchOutcomeService matchOutcome,
      List<SocketEventBinder> eventBinders,
      ActiveRoomRepository activeRoomRepository,
      RoomRehydrationService roomRehydrationService) {
    this.gameInputRules = gameInputRules;
    this.runtimeConfigService = runtimeConfigService;
    this.xoTieBreakInteractionService = xoTieBreakInteractionService;
    this.avoidBombsTieBreakInteractionService = avoidBombsTieBreakInteractionService;
    this.snapshotFactory = snapshotFactory;
    this.scale = scale;
    this.roomRegistry = roomRegistry;
    this.snapshotCoordinator = snapshotCoordinator;
    this.roomExecutors = roomExecutors;
    this.roomTimers = roomTimers;
    this.broadcaster = broadcaster;
    this.store = store;
    this.rulesResolver = rulesResolver;
    this.lifecycle = lifecycle;
    this.matchmakingAllocator = matchmakingAllocator;
    this.castlePlacement = castlePlacement;
    this.claimPhase = claimPhase;
    this.battle = battle;
    this.matchOutcome = matchOutcome;
    this.eventBinders = eventBinders;
    this.activeRoomRepository = activeRoomRepository;
    this.roomRehydrationService = roomRehydrationService;
    Gauge.builder("sok.realtime.rooms", store.rooms(), Map::size).register(meterRegistry);
    Gauge.builder("sok.realtime.players_online", this, SocketGateway::currentOnlinePlayers)
        .register(meterRegistry);
  }

  public void register(
      SocketIOServer server, AuthTokenService authTokenService, boolean allowInsecureSocket) {
    broadcaster.attach(server);
    for (SocketEventBinder binder : eventBinders) {
      binder.bind(server);
    }
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
          final String joinMatchMode = payload.path("matchMode").asText("");
          final String joinRulesetId = payload.path("rulesetId").asText("");
          final String joinMapId = payload.path("mapId").asText("");
          String privateCode = payload.path("privateCode").asText("");
          String normalized = gameInputRules.normalizePrivateCode(privateCode);
          String resolvedRoomId = store.roomIdForUid(uid);
          if (resolvedRoomId == null) {
            resolvedRoomId = activeRoomRepository.findRoomIdByUid(uid).orElse(null);
          }
          if (resolvedRoomId != null && store.get(resolvedRoomId) == null) {
            roomRehydrationService.hydrateRoomFromDbIfAbsent(resolvedRoomId);
          }
          final String existingRoomId = store.roomIdForUid(uid);
          if (existingRoomId != null) {
            if (!submitToRoom(
                existingRoomId,
                new Runnable() {
                  @Override
                  public void run() {
                    RoomState room = store.get(existingRoomId);
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

          MatchmakingAllocator.Allocation allocation =
              matchmakingAllocator.findOrCreateRoomAllocation(normalized);
          if (allocation == null) {
            client.sendEvent("join_rejected", mapOf("reason", "capacity"));
            return;
          }
          final String assignedRoomId = allocation.roomId();

          if (!submitToRoom(
              assignedRoomId,
              new Runnable() {
                @Override
                public void run() {
                  RoomState room = store.get(assignedRoomId);
                  if (room == null) return;
                  GameRuntimeConfig cfg = runtimeConfigService.get();
                  if (room.players.size() >= cfg.getMaxPlayers()) {
                    client.sendEvent("join_rejected", mapOf("reason", "room_full"));
                    return;
                  }
                  if (room.players.isEmpty()) {
                    if (!joinMatchMode.isEmpty()) {
                      room.matchMode =
                          RoomStateFactory.normalizeMatchMode(
                              joinMatchMode, cfg.getDefaultMatchMode());
                    }
                    if (!joinRulesetId.isEmpty()) {
                      room.rulesetId = joinRulesetId;
                    }
                    if (!joinMapId.isEmpty()) {
                      room.mapId = joinMapId;
                    }
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
                  store.mapUidToRoom(uid, room.id);
                  room.hostUid = room.players.get(0).uid;
                  client.joinRoom(room.id);
                  room.lastActivityAt = System.currentTimeMillis();
                  matchmakingAllocator.updateSoloPublicIndexAfterJoin(room);
                  emitRoomUpdate(server, room);
                  if (room.players.size() >= requiredPlayersToStart(room, cfg)
                      && PHASE_WAITING.equals(room.phase)) {
                    startCastlePlacementPhase(server, room);
                  }
                }
              })) {
            rollbackOrphanWaitingRoom(assignedRoomId, allocation.brandNewEmpty());
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
          String roomId = store.roomIdForUid(uid);
          if (roomId == null) return;
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = store.get(roomId);
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
              RoomState room = store.get(roomId);
              if (room == null || room.inviteCode == null || !PHASE_WAITING.equals(room.phase)) return;
              if (!uid.equals(room.hostUid)) return;
              GameRuntimeConfig cfg = runtimeConfigService.get();
              if (room.players.size() < requiredPlayersToStart(room, cfg)
                  || room.players.size() > cfg.getMaxPlayers()) return;
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
              RoomState room = store.get(roomId);
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
          log.info("sok evt submit_estimation roomId={} uid={} value={}", roomId, uid, value);
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = store.get(roomId);
              if (room == null) {
                log.warn("sok submit_estimation ignored: room missing roomId={}", roomId);
                return;
              }
              if (!PHASE_CLAIM_Q.equals(room.phase) && !PHASE_TIE.equals(room.phase)) {
                log.warn(
                    "sok submit_estimation ignored: wrong phase roomId={} phase={} (want claiming_question or battle_tiebreaker)",
                    roomId,
                    room.phase);
                return;
              }
              if (value == Integer.MIN_VALUE) {
                log.warn("sok submit_estimation ignored: value missing roomId={} uid={}", roomId, uid);
                return;
              }
              if (!room.playersByUid.containsKey(uid)) {
                log.warn("sok submit_estimation ignored: uid not in room roomId={} uid={}", roomId, uid);
                return;
              }
              if (PHASE_TIE.equals(room.phase)) {
                DuelState duel = room.activeDuel;
                if (duel == null || duel.numericQuestion == null || !"numeric".equals(duel.tiebreakKind)) {
                  log.warn(
                      "sok submit_estimation ignored: tiebreaker is not numeric estimation roomId={}",
                      roomId);
                  return;
                }
                if (!uid.equals(duel.attackerUid) && !uid.equals(duel.defenderUid)) {
                  log.warn("sok submit_estimation ignored: not duel participant roomId={} uid={}", roomId, uid);
                  return;
                }
                if (duel.tiebreakerAnswers.containsKey(uid)) {
                  log.warn("sok submit_estimation ignored: duplicate tiebreak submit roomId={} uid={}", roomId, uid);
                  return;
                }
                AnswerMetric m = new AnswerMetric();
                m.uid = uid;
                m.value = value;
                m.latencyMs = System.currentTimeMillis() - room.phaseStartedAt;
                duel.tiebreakerAnswers.put(uid, m);
                boolean defenderHuman = !"neutral".equals(duel.defenderUid);
                boolean both =
                    duel.tiebreakerAnswers.containsKey(duel.attackerUid)
                        && (!defenderHuman || duel.tiebreakerAnswers.containsKey(duel.defenderUid));
                if (both) {
                  resolveTiebreaker(server, room);
                } else {
                  emitRoomUpdate(server, room);
                }
                return;
              }
              if (room.estimationAnswers.containsKey(uid)) {
                log.warn("sok submit_estimation ignored: duplicate submit roomId={} uid={}", roomId, uid);
                return;
              }
              AnswerMetric m = new AnswerMetric();
              m.uid = uid;
              m.value = value;
              m.latencyMs = System.currentTimeMillis() - room.phaseStartedAt;
              room.estimationAnswers.put(uid, m);
              log.info(
                  "sok submit_estimation accepted roomId={} uid={} answers={}/{}",
                  roomId,
                  uid,
                  room.estimationAnswers.size(),
                  onlinePlayerCount(room));
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
              RoomState room = store.get(roomId);
              if (room == null || !PHASE_CLAIM_PICK.equals(room.phase)) {
                log.warn(
                    "sok claim_region ignored: room or phase roomId={} uid={} phase={}",
                    roomId,
                    uid,
                    room == null ? "null" : room.phase);
                return;
              }
              if (!uid.equals(room.claimTurnUid)) {
                log.warn(
                    "sok claim_region ignored: not your turn roomId={} uid={} claimTurnUid={}",
                    roomId,
                    uid,
                    room.claimTurnUid);
                return;
              }
              Integer left = room.claimPicksLeftByUid.get(uid);
              if (left == null || left <= 0) {
                log.warn("sok claim_region ignored: no picks left roomId={} uid={}", roomId, uid);
                return;
              }
              RegionState r = room.regions.get(regionId);
              if (r == null || r.ownerUid != null) {
                log.warn(
                    "sok claim_region ignored: bad hex or already owned roomId={} uid={} regionId={} hasRegion={} owner={}",
                    roomId,
                    uid,
                    regionId,
                    r != null,
                    r == null ? null : r.ownerUid);
                return;
              }
              r.ownerUid = uid;
              PlayerState p = room.playersByUid.get(uid);
              p.score += pointValue(room, regionId);
              room.scoreByUid.put(uid, p.score);
              room.claimPicksLeftByUid.put(uid, left - 1);
              if (left - 1 <= 0) {
                rotateClaimTurn(room);
              }
              log.info(
                  "sok claim_region ok roomId={} uid={} regionId={} picksLeftForUid={} neutralRemaining={}",
                  roomId,
                  uid,
                  regionId,
                  room.claimPicksLeftByUid.get(uid),
                  countNeutralRegions(room));
              emitRoomUpdate(server, room);
              if (allRegionsClaimed(room)) {
                log.info("sok all regions claimed → battle roomId={}", roomId);
                startBattlePhase(server, room);
              } else if (claimsQueueEmpty(room)) {
                log.info(
                    "sok claim queue empty, neutralRemaining={} → next estimation roomId={}",
                    countNeutralRegions(room),
                    roomId);
                startClaimingQuestionRound(server, room);
              }
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
              RoomState room = store.get(roomId);
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
              String defenderUid = targetHex.ownerUid;
              if (defenderUid != null && sameTeam(room, attackerUid, defenderUid)) {
                client.sendEvent("attack_invalid", mapOf("reason", "ally_territory"));
                return;
              }
              if (!canAttackRegion(room, attackerUid, targetHexId)) {
                client.sendEvent("attack_invalid", mapOf("reason", "not_adjacent"));
                return;
              }
              room.mcqSpeedTieRetries = 0;
              room.tieBreakOverride = null;
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
          log.info(
              "sok evt submit_answer roomId={} uid={} rawAnswerIndex={}",
              roomId,
              uid,
              payload.path("answerIndex"));
          Integer answerIndex =
              gameInputRules.coerceChoiceIndex(
                  payload.path("answerIndex").isMissingNode()
                      ? null
                      : payload.path("answerIndex").asText());
          if (answerIndex == null && payload.path("answerIndex").asInt(99999) == -1) {
            answerIndex = -1;
          }
          if (answerIndex == null) {
            log.warn("sok submit_answer rejected: could not coerce answerIndex roomId={} uid={}", roomId, uid);
            return;
          }
          final int finalAnswer = answerIndex;
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = store.get(roomId);
              if (room == null) {
                log.warn("sok submit_answer ignored: room missing roomId={}", roomId);
                return;
              }
              if (room.activeDuel == null || PHASE_TIE.equals(room.phase)) {
                log.warn(
                    "sok submit_answer ignored: MCQ only in duel phase (use submit_estimation in tiebreaker) roomId={} phase={} hasDuel={}",
                    roomId,
                    room.phase,
                    room.activeDuel != null);
                return;
              }
              DuelState duel = room.activeDuel;
              boolean participant = uid.equals(duel.attackerUid) || uid.equals(duel.defenderUid);
              if (!participant || duel.answers.containsKey(uid)) {
                log.warn(
                    "sok submit_answer ignored: not participant or duplicate roomId={} uid={} participant={} duplicate={}",
                    roomId,
                    uid,
                    participant,
                    duel.answers.containsKey(uid));
                return;
              }
              DuelAnswer ans = new DuelAnswer();
              ans.answerIndex = finalAnswer;
              ans.timeTaken =
                  Math.min(
                      runtimeConfigService.get().getDuelDurationMs(),
                      System.currentTimeMillis() - room.phaseStartedAt);
              duel.answers.put(uid, ans);
              log.info(
                  "sok submit_answer accepted roomId={} uid={} idx={} duelAnswerCount={}",
                  roomId,
                  uid,
                  finalAnswer,
                  duel.answers.size());
              boolean done = duel.answers.containsKey(duel.attackerUid);
              if (!"neutral".equals(duel.defenderUid)) done = done && duel.answers.containsKey(duel.defenderUid);
              if (done) resolveDuel(server, room);
              else emitRoomUpdate(server, room);
            }
          });
        });

    server.addEventListener(
        "tiebreaker_xo_move",
        JsonNode.class,
        (client, payload, ack) -> {
          validateUidOrDisconnect(client, payload);
          String roomId = asString(payload, "roomId");
          String uid = asString(payload, "uid");
          int cellIndex = payload.path("cellIndex").asInt(-1);
          submitToRoom(
              roomId,
              new Runnable() {
                @Override
                public void run() {
                  RoomState room = store.get(roomId);
                  if (room == null || !PHASE_TIE.equals(room.phase) || room.activeDuel == null) {
                    return;
                  }
                  DuelState duel = room.activeDuel;
                  if (!"xo".equals(duel.tiebreakKind) || duel.xoCells == null) {
                    log.warn(
                        "sok tiebreaker_xo_move ignored: wrong tiebreak kind roomId={}",
                        roomId);
                    return;
                  }
                  if (!uid.equals(duel.xoTurnUid)) {
                    log.warn(
                        "sok tiebreaker_xo_move ignored: not your turn roomId={} uid={} turnUid={}",
                        roomId,
                        uid,
                        duel.xoTurnUid);
                    return;
                  }
                  if (!uid.equals(duel.attackerUid) && !uid.equals(duel.defenderUid)) {
                    return;
                  }
                  if (cellIndex < 0 || cellIndex > 8) {
                    log.warn(
                        "sok tiebreaker_xo_move ignored: bad cell roomId={} cellIndex={}",
                        roomId,
                        cellIndex);
                    return;
                  }
                  GameRuntimeConfig cfg = runtimeConfigService.get();
                  XoTieBreakInteractionService.MoveOutcome mo =
                      xoTieBreakInteractionService.applyMove(duel, uid, cellIndex, cfg);
                  switch (mo.outcomeType()) {
                    case INVALID_OCCUPIED:
                      client.sendEvent("tiebreaker_xo_invalid", mapOf("reason", "occupied"));
                      return;
                    case ATTACKER_WIN:
                      finishBattle(server, room, true, true, true, true, duel);
                      return;
                    case DEFENDER_WIN:
                      finishBattle(server, room, false, true, true, true, duel);
                      return;
                    case DRAW_REPLAY:
                      server
                          .getRoomOperations(room.id)
                          .sendEvent(
                              "tiebreaker_xo_replay",
                              XoTieBreakPayloadFactory.replayPayload(room.id, mo.replayNumber()));
                      emitRoomUpdate(server, room);
                      return;
                    case DRAW_DEFENDER_WINS:
                      finishBattle(server, room, false, true, true, true, duel);
                      return;
                    case CONTINUE:
                      emitRoomUpdate(server, room);
                      return;
                    default:
                      throw new IllegalStateException("Unhandled X-O outcome");
                  }
                }
              });
        });

    server.addEventListener(
        "tiebreaker_avoid_bombs_place",
        JsonNode.class,
        (client, payload, ack) -> {
          validateUidOrDisconnect(client, payload);
          String roomId = asString(payload, "roomId");
          String uid = asString(payload, "uid");
          List<Integer> cells = new ArrayList<Integer>();
          JsonNode cellsNode = payload.path("cells");
          if (cellsNode.isArray()) {
            for (JsonNode c : cellsNode) cells.add(c.asInt(-1));
          }
          submitToRoom(
              roomId,
              new Runnable() {
                @Override
                public void run() {
                  RoomState room = store.get(roomId);
                  if (room == null || !PHASE_TIE.equals(room.phase) || room.activeDuel == null) {
                    return;
                  }
                  DuelState duel = room.activeDuel;
                  AvoidBombsTieBreakInteractionService.MoveOutcome out =
                      avoidBombsTieBreakInteractionService.placeBombs(duel, uid, cells);
                  switch (out.outcomeType()) {
                    case INVALID_PHASE:
                      client.sendEvent(
                          "tiebreaker_avoid_bombs_invalid", mapOf("reason", "bad_phase"));
                      return;
                    case INVALID_PARTICIPANT:
                      client.sendEvent(
                          "tiebreaker_avoid_bombs_invalid", mapOf("reason", "not_participant"));
                      return;
                    case INVALID_DUPLICATE_PLACEMENT:
                      client.sendEvent(
                          "tiebreaker_avoid_bombs_invalid", mapOf("reason", "already_placed"));
                      return;
                    case INVALID_LAYOUT:
                      client.sendEvent(
                          "tiebreaker_avoid_bombs_invalid", mapOf("reason", "bad_layout"));
                      return;
                    case PLACEMENT_ACCEPTED:
                      client.sendEvent(
                          "tiebreaker_avoid_bombs_placed",
                          AvoidBombsTieBreakPayloadFactory.placementAckPayload(
                              room.id, uid, duel.avoidBombsBoards.get(uid)));
                      return;
                    case PLACEMENT_BOTH_READY:
                      client.sendEvent(
                          "tiebreaker_avoid_bombs_placed",
                          AvoidBombsTieBreakPayloadFactory.placementAckPayload(
                              room.id, uid, duel.avoidBombsBoards.get(uid)));
                      cancelTimer(
                          room, AvoidBombsTieBreakerAttackPhaseStrategy.PLACEMENT_TIMER_KEY);
                      server
                          .getRoomOperations(room.id)
                          .sendEvent(
                              "tiebreaker_avoid_bombs_ready",
                              AvoidBombsTieBreakPayloadFactory.readyPayload(room.id, duel));
                      emitRoomUpdate(server, room);
                      return;
                    default:
                      return;
                  }
                }
              });
        });

    server.addEventListener(
        "tiebreaker_avoid_bombs_open",
        JsonNode.class,
        (client, payload, ack) -> {
          validateUidOrDisconnect(client, payload);
          String roomId = asString(payload, "roomId");
          String uid = asString(payload, "uid");
          int cellIndex = payload.path("cellIndex").asInt(-1);
          submitToRoom(
              roomId,
              new Runnable() {
                @Override
                public void run() {
                  RoomState room = store.get(roomId);
                  if (room == null || !PHASE_TIE.equals(room.phase) || room.activeDuel == null) {
                    return;
                  }
                  DuelState duel = room.activeDuel;
                  AvoidBombsTieBreakInteractionService.MoveOutcome out =
                      avoidBombsTieBreakInteractionService.openCell(duel, uid, cellIndex);
                  switch (out.outcomeType()) {
                    case INVALID_PHASE:
                      client.sendEvent(
                          "tiebreaker_avoid_bombs_invalid", mapOf("reason", "bad_phase"));
                      return;
                    case INVALID_PARTICIPANT:
                      client.sendEvent(
                          "tiebreaker_avoid_bombs_invalid", mapOf("reason", "not_participant"));
                      return;
                    case INVALID_NOT_YOUR_TURN:
                      client.sendEvent(
                          "tiebreaker_avoid_bombs_invalid", mapOf("reason", "not_your_turn"));
                      return;
                    case INVALID_CELL:
                      client.sendEvent(
                          "tiebreaker_avoid_bombs_invalid", mapOf("reason", "bad_cell"));
                      return;
                    case INVALID_ALREADY_OPENED:
                      client.sendEvent(
                          "tiebreaker_avoid_bombs_invalid", mapOf("reason", "already_opened"));
                      return;
                    case REVEAL_SAFE:
                    case REVEAL_BOMB:
                      server
                          .getRoomOperations(room.id)
                          .sendEvent(
                              "tiebreaker_avoid_bombs_reveal",
                              AvoidBombsTieBreakPayloadFactory.revealPayload(
                                  room.id,
                                  duel,
                                  out.openerUid(),
                                  out.targetUid(),
                                  out.cellIndex(),
                                  out.isBomb(),
                                  out.nextTurnUid()));
                      emitRoomUpdate(server, room);
                      return;
                    case ATTACKER_WIN:
                    case DEFENDER_WIN:
                      server
                          .getRoomOperations(room.id)
                          .sendEvent(
                              "tiebreaker_avoid_bombs_reveal",
                              AvoidBombsTieBreakPayloadFactory.revealPayload(
                                  room.id,
                                  duel,
                                  out.openerUid(),
                                  out.targetUid(),
                                  out.cellIndex(),
                                  out.isBomb(),
                                  null));
                      server
                          .getRoomOperations(room.id)
                          .sendEvent(
                              "tiebreaker_avoid_bombs_reveal_all",
                              AvoidBombsTieBreakPayloadFactory.revealAllPayload(room.id, duel));
                      finishBattle(
                          server,
                          room,
                          out.outcomeType()
                              == AvoidBombsTieBreakInteractionService.OutcomeType.ATTACKER_WIN,
                          true,
                          true,
                          true,
                          duel);
                      return;
                    default:
                      return;
                  }
                }
              });
        });

    server.addDisconnectListener(
        client -> {
          Object uidObj = client.get("uid");
          if (uidObj == null) return;
          String uid = String.valueOf(uidObj);
          String roomId = store.roomIdForUid(uid);
          if (roomId == null) return;
          submitToRoom(roomId, new Runnable() {
            @Override
            public void run() {
              RoomState room = store.get(roomId);
              if (room == null) return;
              PlayerState p = room.playersByUid.get(uid);
              if (p == null) return;
              p.online = false;
              p.lastSeenAt = System.currentTimeMillis();
              scheduleCleanup(room);
              emitRoomUpdate(server, room);
            }
          });
        });

    roomTimers.scheduleAtFixedRate(
        () -> {
          RoomRegistryService reg = roomRegistry.getIfAvailable();
          if (reg != null) {
            for (RoomState room : store.values()) {
              reg.refresh(room.id);
            }
          }
          maybePublishRoomSnapshots();
          for (RoomState room : store.values()) {
            submitToRoom(
                room.id,
                () -> {
                  evaluateEndConditions(server, room);
                  lifecycle.evictDisconnectedPlayers(room);
                });
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

  private int requiredPlayersToStart(RoomState room, GameRuntimeConfig cfg) {
    return rulesResolver.requiredPlayersToStart(room, cfg);
  }

  private boolean sameTeam(RoomState room, String uidA, String uidB) {
    return rulesResolver.sameTeam(room, uidA, uidB);
  }

  private void maybePublishRoomSnapshots() {
    if (!scale.isSnapshotToRedis()) {
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
    for (RoomState room : store.values()) {
      snapshotCoordinator.snapshotHot(room);
    }
  }

  private void rollbackOrphanWaitingRoom(String roomId, boolean brandNewEmpty) {
    if (matchmakingAllocator.isOrphanWaitingRoom(roomId, brandNewEmpty)) {
      lifecycle.shutdownRoom(roomId);
    }
  }

  private boolean submitToRoom(String roomId, Runnable task) {
    return roomExecutors.submitToRoom(roomId, task);
  }

  public int roomCount() {
    return store.size();
  }

  /** Active worker threads processing room mutations (approximate). */
  public int roomExecutorCount() {
    return roomExecutors.activeWorkerCount();
  }

  public int roomWorkerQueueDepth() {
    return roomExecutors.queueDepth();
  }

  public boolean isHealthy() {
    return roomExecutors.hasQueueHeadroom();
  }

  public int currentOnlinePlayers() {
    int c = 0;
    for (RoomState r : store.values()) {
      for (PlayerState p : r.players) {
        if (p.online && !p.isEliminated) c++;
      }
    }
    return c;
  }

  private void removePlayerFromRoom(SocketIOServer server, RoomState room, String uid) {
    lifecycle.removePlayerFromRoom(room, uid);
  }

  private void startCastlePlacementPhase(SocketIOServer server, RoomState room) {
    castlePlacement.startCastlePlacementPhase(server, room);
  }

  private void startClaimingQuestionRound(SocketIOServer server, RoomState room) {
    claimPhase.startClaimingQuestionRound(server, room);
  }

  private void resolveEstimationRound(SocketIOServer server, RoomState room) {
    claimPhase.resolveEstimationRound(server, room);
  }

  private void rotateClaimTurn(RoomState room) {
    claimPhase.rotateClaimTurn(room);
  }

  private boolean claimsQueueEmpty(RoomState room) {
    return claimPhase.claimsQueueEmpty(room);
  }

  private boolean allRegionsClaimed(RoomState room) {
    return claimPhase.allRegionsClaimed(room);
  }

  private int countNeutralRegions(RoomState room) {
    return claimPhase.countNeutralRegions(room);
  }

  private boolean allPlayersPlacedCastle(RoomState room) {
    return castlePlacement.allPlayersPlacedCastle(room);
  }

  private void startBattlePhase(SocketIOServer server, RoomState room) {
    battle.startBattlePhase(server, room);
  }

  private boolean canAttackRegion(RoomState room, String attackerUid, int targetRegionId) {
    return battle.canAttackRegion(room, attackerUid, targetRegionId);
  }

  private void startDuel(
      SocketIOServer server, RoomState room, String attackerUid, int targetRegionId, boolean tieBreakerAttack) {
    battle.startDuel(server, room, attackerUid, targetRegionId, tieBreakerAttack);
  }

  private void resolveDuel(SocketIOServer server, RoomState room) {
    battle.resolveDuel(server, room);
  }

  private void resolveTiebreaker(SocketIOServer server, RoomState room) {
    battle.resolveTiebreaker(server, room);
  }

  private void finishBattle(
      SocketIOServer server,
      RoomState room,
      boolean attackerWins,
      boolean attackerCorrect,
      boolean defenderCorrect,
      boolean tieBreakerMinigame,
      DuelState duel) {
    battle.finishBattle(
        server, room, attackerWins, attackerCorrect, defenderCorrect, tieBreakerMinigame, duel);
  }

  private void evaluateEndConditions(SocketIOServer server, RoomState room) {
    matchOutcome.evaluateEndConditions(server, room);
  }

  private void cancelTimer(RoomState room, String key) {
    roomTimers.cancelTimer(room, key);
  }

  private void scheduleCleanup(RoomState room) {
    lifecycle.scheduleCleanup(room);
  }

  private void emitRoomUpdate(SocketIOServer server, RoomState room) {
    broadcaster.emitRoomUpdate(room);
  }

  private int onlinePlayerCount(RoomState room) {
    return snapshotFactory.onlinePlayerCount(room);
  }

  private int pointValue(RoomState room, int regionId) {
    return snapshotFactory.pointValue(room, regionId);
  }

  @Override
  public void destroy() {
    for (String roomId : new ArrayList<String>(store.rooms().keySet())) {
      lifecycle.shutdownRoom(roomId);
    }
  }
}
