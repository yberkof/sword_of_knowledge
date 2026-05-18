package com.sok.backend.realtime.actor.phase;

import com.sok.backend.domain.game.ClaimingPhaseService;
import com.sok.backend.realtime.RealtimeConstants;
import com.sok.backend.realtime.actor.RoomBehaviorFactory;
import com.sok.backend.realtime.actor.RoomCommand;
import com.sok.backend.realtime.actor.RoomDeps;
import com.sok.backend.realtime.actor.RoomHelpers;
import com.sok.backend.realtime.actor.RoomSessionHub;
import com.sok.backend.realtime.domain.GameState;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

import java.time.Duration;
import java.util.*;

/**
 * Battle map phase, MCQ duels, and numeric tie-breakers.
 */
public final class BattlePhase {

    private final RoomDeps deps;
    private final RoomBehaviorFactory behaviors;
    private final SettlementPhase settlement;

    public BattlePhase(RoomDeps deps, RoomBehaviorFactory behaviors, SettlementPhase settlement) {
        this.deps = deps;
        this.behaviors = behaviors;
        this.settlement = settlement;
    }

    public Behavior<RoomCommand> startBattle(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        int turnIdx = RoomHelpers.firstAliveTurnIndex(state);
        var newState = new GameState(
            state.id(), RealtimeConstants.P_BATTLE, state.hostUid(), Math.max(1, state.round()), turnIdx,
            System.currentTimeMillis(), null,
            state.players(), state.regions(), null, List.of(), Map.of(), Map.of(), null, null,
            state.sessions()
        );

        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.Event(
            RealtimeConstants.E_PHASE_CHANGED, Map.of("phase", RealtimeConstants.P_BATTLE, "round", newState.round())));
        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, newState)));
        return behaviors.active(ctx, timers, newState);
    }

    public Behavior<RoomCommand> onAttack(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            RoomCommand.Attack m) {

        if (!RealtimeConstants.P_BATTLE.equals(state.phase())) return behaviors.active(ctx, timers, state);

        String turnUid = RoomHelpers.turnUidAt(state);
        if (!m.attackerUid().equals(turnUid)) return behaviors.active(ctx, timers, state);

        var target = state.regions().get(m.targetHexId());
        if (target == null || m.attackerUid().equals(target.ownerUid())) return behaviors.active(ctx, timers, state);
        if (!RoomHelpers.isAdjacentToOwner(state, m.attackerUid(), m.targetHexId())) {
            return behaviors.active(ctx, timers, state);
        }

        String defenderUid = target.ownerUid() != null ? target.ownerUid() : "neutral";
        var mcq = deps.questionEngine().nextMcqQuestion(RoomHelpers.cfg(deps).getDefaultQuestionCategory());
        long duelMs = RoomHelpers.cfg(deps).getDuelMcqMs();
        long now = System.currentTimeMillis();

        var duel = new GameState.DuelState(m.attackerUid(), defenderUid, m.targetHexId(), mcq, new HashMap<>());
        var newState = new GameState(
            state.id(), RealtimeConstants.P_DUEL, state.hostUid(), state.round(), state.currentTurnIndex(),
            now, now + duelMs, state.players(), state.regions(),
            null, List.of(), Map.of(), Map.of(), null, duel, state.sessions()
        );

        Map<String, Object> qPayload = deps.questionEngine().toClient(mcq, now, duelMs);
        Map<String, Object> duelPayload = new HashMap<>(qPayload);
        duelPayload.put("question", qPayload);
        duelPayload.put("attackerUid", m.attackerUid());
        duelPayload.put("defenderUid", defenderUid);
        duelPayload.put("targetHexId", m.targetHexId());
        duelPayload.put("duelDurationMs", duelMs);
        duelPayload.put("serverNowMs", now);
        duelPayload.put("phaseEndsAt", now + duelMs);

        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, newState)));
        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.Event(RealtimeConstants.E_DUEL_START, duelPayload));
        timers.startSingleTimer("duel-timer", new RoomCommand.TimerTick("duel_timeout"), Duration.ofMillis(duelMs));
        return behaviors.active(ctx, timers, newState);
    }

    public Behavior<RoomCommand> onSubmitAnswer(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            RoomCommand.SubmitAnswer m) {

        if (!RealtimeConstants.P_DUEL.equals(state.phase()) || state.activeDuel() == null) {
            return behaviors.active(ctx, timers, state);
        }

        var duel = state.activeDuel();
        if (!m.uid().equals(duel.attackerUid()) && !m.uid().equals(duel.defenderUid())) {
            return behaviors.active(ctx, timers, state);
        }

        var answers = new HashMap<>(duel.answers());
        answers.put(m.uid(), new GameState.DuelAnswer(m.answerIndex(),
            Math.max(0, m.timestamp() - state.phaseStartedAt())));
        var newDuel = new GameState.DuelState(
            duel.attackerUid(), duel.defenderUid(), duel.targetRegionId(), duel.mcqQuestion(),
            Collections.unmodifiableMap(answers));
        var newState = RoomHelpers.withDuel(state, newDuel);

        boolean attDone = answers.containsKey(duel.attackerUid());
        boolean defDone = "neutral".equals(duel.defenderUid()) || answers.containsKey(duel.defenderUid());
        if (attDone && defDone) {
            return resolveDuel(ctx, timers, newState, false);
        }
        return behaviors.active(ctx, timers, newState);
    }

    public Behavior<RoomCommand> onSubmitEstimationTiebreak(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            RoomCommand.SubmitEstimation m) {

        if (!RealtimeConstants.P_TIEBREAKER.equals(state.phase()) || state.activeDuel() == null) {
            return behaviors.active(ctx, timers, state);
        }

        var duel = state.activeDuel();
        if (!m.uid().equals(duel.attackerUid()) && !m.uid().equals(duel.defenderUid())) {
            return behaviors.active(ctx, timers, state);
        }

        var answers = new HashMap<>(state.estimationAnswers());
        answers.put(m.uid(), new GameState.AnswerMetric(m.uid(), m.value(),
            Math.max(0, m.timestamp() - state.phaseStartedAt())));

        var newState = RoomHelpers.withEstimation(state, answers);
        boolean attDone = answers.containsKey(duel.attackerUid());
        boolean defDone = "neutral".equals(duel.defenderUid()) || answers.containsKey(duel.defenderUid());
        if (attDone && defDone) {
            return resolveTiebreak(ctx, timers, newState, false);
        }
        return behaviors.active(ctx, timers, newState);
    }

    public Behavior<RoomCommand> resolveDuel(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            boolean fromTimeout) {

        timers.cancel("duel-timer");
        var duel = state.activeDuel();
        if (duel == null) return behaviors.active(ctx, timers, state);

        var answers = new HashMap<>(duel.answers());
        long maxLatency = RoomHelpers.cfg(deps).getDuelMcqMs();
        if (!answers.containsKey(duel.attackerUid())) {
            answers.put(duel.attackerUid(), new GameState.DuelAnswer(-1, maxLatency));
        }
        if (!"neutral".equals(duel.defenderUid()) && !answers.containsKey(duel.defenderUid())) {
            answers.put(duel.defenderUid(), new GameState.DuelAnswer(-1, maxLatency));
        }

        var att = answers.get(duel.attackerUid());
        var def = answers.get(duel.defenderUid());
        int correct = duel.mcqQuestion().correctIndex;

        boolean attCorrect = att != null && att.answerIndex() == correct;
        boolean defCorrect = def != null && def.answerIndex() == correct;

        if ("neutral".equals(duel.defenderUid())) {
            if (attCorrect) return finishDuel(ctx, timers, state, duel, true, answers);
            return finishDuel(ctx, timers, state, duel, false, answers);
        }

        if (!attCorrect && !defCorrect) {
            return finishDuel(ctx, timers, state, duel, false, answers);
        }
        if (attCorrect && !defCorrect) {
            return finishDuel(ctx, timers, state, duel, true, answers);
        }
        if (!attCorrect && defCorrect) {
            return finishDuel(ctx, timers, state, duel, false, answers);
        }

        return startTiebreak(ctx, timers, state, duel, answers);
    }

    public Behavior<RoomCommand> startTiebreak(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            GameState.DuelState duel,
            Map<String, GameState.DuelAnswer> mcqAnswers) {

        var q = deps.questionEngine().nextNumericQuestion(RoomHelpers.cfg(deps).getDefaultQuestionCategory());
        long tbMs = RoomHelpers.cfg(deps).getTiebreakNumericMs();
        long now = System.currentTimeMillis();

        var newDuel = new GameState.DuelState(
            duel.attackerUid(), duel.defenderUid(), duel.targetRegionId(), duel.mcqQuestion(),
            Collections.unmodifiableMap(new HashMap<>(mcqAnswers)));

        var newState = new GameState(
            state.id(), RealtimeConstants.P_TIEBREAKER, state.hostUid(), state.round(), state.currentTurnIndex(),
            now, now + tbMs, state.players(), state.regions(),
            null, List.of(), Map.of(), Map.of(), q, newDuel, state.sessions()
        );

        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, newState)));
        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.Event(
            RealtimeConstants.E_TIEBREAKER_START,
            deps.questionEngine().toClient(q, now, tbMs)));
        timers.startSingleTimer("tiebreak-timer", new RoomCommand.TimerTick("tiebreak_timeout"), Duration.ofMillis(tbMs));
        return behaviors.active(ctx, timers, newState);
    }

    public Behavior<RoomCommand> resolveTiebreak(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            boolean fromTimeout) {

        timers.cancel("tiebreak-timer");
        var duel = state.activeDuel();
        if (duel == null || state.activeNumericQuestion() == null) {
            return behaviors.active(ctx, timers, state);
        }

        int correct = state.activeNumericQuestion().answer;
        var ranked = tiebreakMetrics(state, duel);
        var legacyMetrics = ranked.stream().map(x -> {
            var am = new com.sok.backend.realtime.model.AnswerMetric();
            am.uid = x.uid;
            am.value = x.value;
            am.latencyMs = x.latencyMs;
            return am;
        }).toList();

        RoomSessionHub.broadcast(state, new RoomCommand.RoomEvent.Event(
            RealtimeConstants.E_ANSWER_EVENT,
            deps.snapshotFactory().rankingsPayload(legacyMetrics, Map.of(), correct)));

        timers.startSingleTimer("phase-timer",
            new RoomCommand.TimerTick("tiebreak_reveal_margin"),
            Duration.ofMillis(RoomHelpers.cfg(deps).getAnswerAnimationMs() + RoomHelpers.cfg(deps).getRankingSlideInMs()));

        return behaviors.active(ctx, timers, state);
    }

    public Behavior<RoomCommand> finishTiebreakAfterReveal(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        timers.cancel("phase-timer");
        var duel = state.activeDuel();
        if (duel == null || state.activeNumericQuestion() == null) {
            return behaviors.active(ctx, timers, state);
        }

        var ranked = tiebreakMetrics(state, duel);
        boolean attackerWins = !ranked.isEmpty() && ranked.get(0).uid.equals(duel.attackerUid());
        return finishDuel(ctx, timers, state, duel, attackerWins, duel.answers());
    }

    private List<ClaimingPhaseService.Metric> tiebreakMetrics(GameState state, GameState.DuelState duel) {
        var metrics = new ArrayList<ClaimingPhaseService.Metric>();
        long maxLat = RoomHelpers.cfg(deps).getTiebreakNumericMs();
        for (String uid : List.of(duel.attackerUid(), duel.defenderUid())) {
            if ("neutral".equals(uid)) continue;
            var m = state.estimationAnswers().get(uid);
            var res = new ClaimingPhaseService.Metric();
            res.uid = uid;
            if (m == null) {
                res.value = 0;
                res.latencyMs = maxLat;
            } else {
                res.value = m.value();
                res.latencyMs = m.latencyMs();
            }
            metrics.add(res);
        }
        int correct = state.activeNumericQuestion().answer;
        return deps.claimingService().rankByDeltaThenLatency(metrics, correct);
    }

    public Behavior<RoomCommand> finishDuel(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            GameState.DuelState duel,
            boolean attackerWins,
            Map<String, GameState.DuelAnswer> answers) {

        var regions = new HashMap<>(state.regions());
        var players = new HashMap<>(state.players());
        var target = regions.get(duel.targetRegionId());
        String winnerUid = attackerWins ? duel.attackerUid() : duel.defenderUid();

        if (attackerWins && target != null) {
            String defenderOwner = target.ownerUid();
            boolean isEnemyCastle = target.isCastle()
                && defenderOwner != null
                && !defenderOwner.equals(duel.attackerUid());

            if (isEnemyCastle) {
                var def = players.get(duel.defenderUid());
                if (def != null) {
                    int hp = def.castleHp() - 1;
                    if (hp <= 0) {
                        RoomHelpers.eliminatePlayer(players, regions, duel.defenderUid(), duel.attackerUid());
                    } else {
                        players.put(duel.defenderUid(),
                            RoomHelpers.copyPlayer(def, hp, def.score(), def.castleRegionId(), false));
                    }
                }
            } else {
                regions.put(duel.targetRegionId(),
                    new GameState.Region(target.id(), duel.attackerUid(), target.isCastle(), target.neighbors()));
                var att = players.get(duel.attackerUid());
                if (att != null) {
                    players.put(duel.attackerUid(),
                        RoomHelpers.copyPlayer(att, att.castleHp(), att.score() + 1, att.castleRegionId(), false));
                }
            }
        }

        int nextTurn = RoomHelpers.nextAliveTurnIndex(state, state.currentTurnIndex());
        var battleState = new GameState(
            state.id(), RealtimeConstants.P_BATTLE, state.hostUid(), state.round(), nextTurn,
            System.currentTimeMillis(), null,
            Collections.unmodifiableMap(players), Collections.unmodifiableMap(regions),
            null, List.of(), Map.of(), Map.of(), null, null, state.sessions()
        );

        int correctIdx = duel.mcqQuestion().correctIndex;
        var attAns = answers.get(duel.attackerUid());
        var defAns = answers.get(duel.defenderUid());

        Map<String, Object> result = new HashMap<>();
        result.put("attackerWins", attackerWins);
        result.put("winnerUid", winnerUid);
        result.put("attackerUid", duel.attackerUid());
        result.put("defenderUid", duel.defenderUid());
        result.put("targetHexId", duel.targetRegionId());
        result.put("correctIndex", correctIdx);
        result.put("attackerCorrect", attAns != null && attAns.answerIndex() == correctIdx);
        result.put("defenderCorrect", defAns != null && defAns.answerIndex() == correctIdx);
        result.put("attackerAnswerIndex", attAns != null ? attAns.answerIndex() : -1);
        result.put("defenderAnswerIndex", defAns != null ? defAns.answerIndex() : -1);
        result.put("tieBreakerMinigame", RealtimeConstants.P_TIEBREAKER.equals(state.phase()));
        if (RealtimeConstants.P_TIEBREAKER.equals(state.phase()) && state.activeNumericQuestion() != null) {
            result.put("correctNumericAnswer", state.activeNumericQuestion().answer);
            var attEst = state.estimationAnswers().get(duel.attackerUid());
            var defEst = state.estimationAnswers().get(duel.defenderUid());
            if (attEst != null) result.put("attackerEstimate", attEst.value());
            if (defEst != null) result.put("defenderEstimate", defEst.value());
        }

        RoomSessionHub.broadcast(battleState, new RoomCommand.RoomEvent.Event(
            RealtimeConstants.E_DUEL_RESOLVED, Map.of("result", result, "room", RoomHelpers.clientSummary(deps, battleState))));
        RoomSessionHub.broadcast(battleState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, battleState)));

        var endCheck = settlement.evaluateEnd(ctx, timers, battleState);
        if (endCheck != null) return endCheck;
        return behaviors.active(ctx, timers, battleState);
    }
}
