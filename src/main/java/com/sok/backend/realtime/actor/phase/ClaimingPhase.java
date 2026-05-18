package com.sok.backend.realtime.actor.phase;

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
 * Expansion claiming: estimation questions, rankings, region picks.
 */
public final class ClaimingPhase {

    private final RoomDeps deps;
    private final RoomBehaviorFactory behaviors;
    private final BattlePhase battle;
    private final Random random;

    public ClaimingPhase(RoomDeps deps, RoomBehaviorFactory behaviors, BattlePhase battle, Random random) {
        this.deps = deps;
        this.behaviors = behaviors;
        this.battle = battle;
        this.random = random;
    }

    public Behavior<RoomCommand> startClaimQ(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        var q = deps.questionEngine().nextNumericQuestion(RoomHelpers.cfg(deps).getDefaultQuestionCategory());
        long turnMs = RoomHelpers.cfg(deps).getClaimDurationMs();

        var newState = new GameState(
            state.id(), RealtimeConstants.P_CLAIM_Q, state.hostUid(), state.round(), state.currentTurnIndex(),
            System.currentTimeMillis(), System.currentTimeMillis() + turnMs,
            state.players(), state.regions(), null, List.of(), Map.of(), Map.of(), q, null,
            state.sessions()
        );

        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, newState)));
        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.Event(
            RealtimeConstants.E_ESTIMATION_QUESTION,
            deps.questionEngine().toClient(q, newState.phaseStartedAt(), turnMs)));

        timers.startSingleTimer("phase-timer", new RoomCommand.TimerTick("resolve_claim_q"), Duration.ofMillis(turnMs));
        return behaviors.active(ctx, timers, newState);
    }

    public Behavior<RoomCommand> onSubmitEstimation(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            RoomCommand.SubmitEstimation m) {

        if (!RealtimeConstants.P_CLAIM_Q.equals(state.phase())) {
            return behaviors.active(ctx, timers, state);
        }

        var answers = new HashMap<>(state.estimationAnswers());
        answers.put(m.uid(), new GameState.AnswerMetric(m.uid(), m.value(),
            Math.max(0, m.timestamp() - state.phaseStartedAt())));

        var newState = RoomHelpers.withEstimation(state, answers);
        long onlineCount = state.players().values().stream().filter(GameState.Player::online).count();
        if (newState.estimationAnswers().size() >= onlineCount) {
            return resolveClaimQ(ctx, timers, newState);
        }
        return behaviors.active(ctx, timers, newState);
    }

    public Behavior<RoomCommand> resolveClaimQ(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        timers.cancel("phase-timer");

        var metrics = state.players().values().stream().map(p -> {
            var m = state.estimationAnswers().get(p.uid());
            var res = new com.sok.backend.domain.game.ClaimingPhaseService.Metric();
            res.uid = p.uid();
            if (m == null) {
                res.value = -1;
                res.latencyMs = RoomHelpers.cfg(deps).getClaimDurationMs();
            } else {
                res.value = m.value();
                res.latencyMs = m.latencyMs();
            }
            return res;
        }).toList();

        int correct = state.activeNumericQuestion().answer;
        var ranked = deps.claimingService().rankByDeltaThenLatency(metrics, correct);
        var picks = deps.claimingService().assignClaimPicks(
            ranked, RoomHelpers.cfg(deps).getClaimFirstPicks(), RoomHelpers.cfg(deps).getClaimSecondPicks());

        List<String> queue = new ArrayList<>();
        ranked.forEach(x -> {
            if (picks.getOrDefault(x.uid, 0) > 0) queue.add(x.uid);
        });

        var legacyMetrics = ranked.stream().map(x -> {
            var am = new com.sok.backend.realtime.model.AnswerMetric();
            am.uid = x.uid;
            am.value = x.value;
            am.latencyMs = x.latencyMs;
            return am;
        }).toList();

        RoomSessionHub.broadcast(state, new RoomCommand.RoomEvent.Event(
            RealtimeConstants.E_ANSWER_EVENT,
            deps.snapshotFactory().rankingsPayload(legacyMetrics, picks, correct)));

        var newState = new GameState(
            state.id(), state.phase(), state.hostUid(), state.round(), state.currentTurnIndex(),
            state.phaseStartedAt(), state.phaseEndsAt(), state.players(), state.regions(),
            null, Collections.unmodifiableList(queue), Collections.unmodifiableMap(picks),
            state.estimationAnswers(), state.activeNumericQuestion(), null, state.sessions()
        );

        timers.startSingleTimer("phase-timer",
            new RoomCommand.TimerTick("claim_rank_margin"),
            Duration.ofMillis(RoomHelpers.cfg(deps).getAnswerAnimationMs() + RoomHelpers.cfg(deps).getRankingSlideInMs()));

        return behaviors.active(ctx, timers, newState);
    }

    public Behavior<RoomCommand> startClaimPick(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        timers.cancel("phase-timer");
        String firstUid = state.claimQueue().isEmpty() ? null : state.claimQueue().get(0);
        long pickMs = RoomHelpers.cfg(deps).getClaimPickTurnMs();
        long pickEndsAt = System.currentTimeMillis() + pickMs;

        var newState = new GameState(
            state.id(), RealtimeConstants.P_CLAIM_P, state.hostUid(), state.round(), state.currentTurnIndex(),
            System.currentTimeMillis(), pickEndsAt, state.players(), state.regions(),
            firstUid, state.claimQueue(), state.claimPicksLeft(),
            Map.of(), null, null, state.sessions()
        );

        int correct = state.activeNumericQuestion() != null ? state.activeNumericQuestion().answer : 0;
        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.Event(
            RealtimeConstants.E_CLAIM_RANKINGS,
            deps.snapshotFactory().claimPickTransitionPayload(firstUid, state.claimPicksLeft(), correct)));
        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, newState)));

        timers.startSingleTimer("pick-timer", new RoomCommand.TimerTick("auto_pick"), Duration.ofMillis(pickMs));
        return behaviors.active(ctx, timers, newState);
    }

    public Behavior<RoomCommand> startPickTimer(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        if (state.claimTurnUid() == null) return rotateClaimTurn(ctx, timers, state);

        timers.cancel("pick-timer");
        long pickMs = RoomHelpers.cfg(deps).getClaimPickTurnMs();
        long pickEndsAt = System.currentTimeMillis() + pickMs;
        var newState = new GameState(
            state.id(), state.phase(), state.hostUid(), state.round(), state.currentTurnIndex(),
            state.phaseStartedAt(), pickEndsAt,
            state.players(), state.regions(), state.claimTurnUid(), state.claimQueue(),
            state.claimPicksLeft(), state.estimationAnswers(), state.activeNumericQuestion(), state.activeDuel(),
            state.sessions()
        );

        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, newState)));
        timers.startSingleTimer("pick-timer", new RoomCommand.TimerTick("auto_pick"), Duration.ofMillis(pickMs));
        return behaviors.active(ctx, timers, newState);
    }

    public Behavior<RoomCommand> onClaimRegion(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            RoomCommand.ClaimRegion m) {

        if (!RealtimeConstants.P_CLAIM_P.equals(state.phase()) || !m.uid().equals(state.claimTurnUid())) {
            return behaviors.active(ctx, timers, state);
        }

        var reg = state.regions().get(m.regionId());
        if (reg == null || reg.ownerUid() != null || !RoomHelpers.isAdjacentToOwner(state, m.uid(), m.regionId())) {
            return behaviors.active(ctx, timers, state);
        }

        timers.cancel("pick-timer");
        return applyClaim(ctx, timers, state, m.uid(), m.regionId());
    }

    private Behavior<RoomCommand> applyClaim(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            String uid,
            int regionId) {

        var reg = state.regions().get(regionId);
        var newRegions = new HashMap<>(state.regions());
        newRegions.put(regionId, new GameState.Region(regionId, uid, false, reg.neighbors()));

        var newPlayers = new HashMap<>(state.players());
        var p = newPlayers.get(uid);
        newPlayers.put(uid, new GameState.Player(
            p.uid(), p.name(), p.color(), p.avatarUrl(), p.online(), p.castleHp(), p.score() + 1,
            p.castleRegionId(), p.isEliminated()));

        int left = state.claimPicksLeft().getOrDefault(uid, 0) - 1;
        var newPicks = new HashMap<>(state.claimPicksLeft());
        newPicks.put(uid, Math.max(0, left));

        var newState = new GameState(
            state.id(), state.phase(), state.hostUid(), state.round(), state.currentTurnIndex(),
            state.phaseStartedAt(), state.phaseEndsAt(),
            Collections.unmodifiableMap(newPlayers), Collections.unmodifiableMap(newRegions),
            state.claimTurnUid(), state.claimQueue(), Collections.unmodifiableMap(newPicks),
            state.estimationAnswers(), state.activeNumericQuestion(), state.activeDuel(), state.sessions()
        );

        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, newState)));

        if (left <= 0) return rotateClaimTurn(ctx, timers, newState);
        return startPickTimer(ctx, timers, newState);
    }

    private Behavior<RoomCommand> rotateClaimTurn(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        List<String> nextQueue = new ArrayList<>(state.claimQueue());
        if (!nextQueue.isEmpty()) nextQueue.remove(0);

        var newState = new GameState(
            state.id(), state.phase(), state.hostUid(), state.round(), state.currentTurnIndex(),
            state.phaseStartedAt(), state.phaseEndsAt(), state.players(), state.regions(),
            nextQueue.isEmpty() ? null : nextQueue.get(0), Collections.unmodifiableList(nextQueue),
            state.claimPicksLeft(), state.estimationAnswers(), state.activeNumericQuestion(), state.activeDuel(),
            state.sessions()
        );

        if (RoomHelpers.allBasicRegionsOwned(newState)) {
            return battle.startBattle(ctx, timers, newState);
        }
        if (nextQueue.isEmpty()) {
            return startClaimQ(ctx, timers, newState);
        }
        return startPickTimer(ctx, timers, newState);
    }

    public Behavior<RoomCommand> autoPick(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        if (state.claimTurnUid() == null) return behaviors.active(ctx, timers, state);

        var adjacent = RoomHelpers.claimableAdjacentRegions(state, state.claimTurnUid());
        if (adjacent.isEmpty()) {
            return skipPickTurn(ctx, timers, state, state.claimTurnUid());
        }

        var target = adjacent.get(random.nextInt(adjacent.size()));
        return applyClaim(ctx, timers, state, state.claimTurnUid(), target.id());
    }

    private Behavior<RoomCommand> skipPickTurn(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            String uid) {

        timers.cancel("pick-timer");
        int left = state.claimPicksLeft().getOrDefault(uid, 0) - 1;
        var newPicks = new HashMap<>(state.claimPicksLeft());
        newPicks.put(uid, Math.max(0, left));

        var newState = new GameState(
            state.id(), state.phase(), state.hostUid(), state.round(), state.currentTurnIndex(),
            state.phaseStartedAt(), state.phaseEndsAt(), state.players(), state.regions(),
            state.claimTurnUid(), state.claimQueue(), Collections.unmodifiableMap(newPicks),
            state.estimationAnswers(), state.activeNumericQuestion(), state.activeDuel(), state.sessions()
        );

        if (left <= 0) return rotateClaimTurn(ctx, timers, newState);
        return startPickTimer(ctx, timers, newState);
    }
}
