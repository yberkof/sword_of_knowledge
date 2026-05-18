package com.sok.backend.realtime.actor;

import com.sok.backend.realtime.RealtimeConstants;
import com.sok.backend.realtime.RoomSnapshotFactory;
import com.sok.backend.realtime.actor.phase.BattlePhase;
import com.sok.backend.realtime.actor.phase.ClaimingPhase;
import com.sok.backend.realtime.actor.phase.LobbyPhase;
import com.sok.backend.realtime.actor.phase.SettlementPhase;
import com.sok.backend.realtime.domain.GameState;
import com.sok.backend.domain.game.ClaimingPhaseService;
import com.sok.backend.domain.game.QuestionEngineService;
import com.sok.backend.service.config.RuntimeGameConfigService;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

import java.util.Map;
import java.util.Random;

/**
 * Sharded game room — routes commands to phase handlers.
 */
public class GameRoomActor {

    public static final EntityTypeKey<RoomCommand> ENTITY_TYPE_KEY =
        EntityTypeKey.create(RoomCommand.class, "GameRoom");

    private final RoomDeps deps;
    private final LobbyPhase lobby;
    private final ClaimingPhase claiming;
    private final BattlePhase battle;
    private final SettlementPhase settlement;

    public static Behavior<RoomCommand> create(
            String roomId,
            QuestionEngineService questionEngine,
            RuntimeGameConfigService config,
            ClaimingPhaseService claimingService,
            RoomSnapshotFactory snapshotFactory) {

        return Behaviors.setup(ctx ->
            Behaviors.withTimers(timers -> {
                var deps = new RoomDeps(questionEngine, config, claimingService, snapshotFactory);
                var actor = new GameRoomActor(deps);
                return actor.lobby.waiting(ctx, timers, GameState.empty(roomId));
            })
        );
    }

    private GameRoomActor(RoomDeps deps) {
        this.deps = deps;
        this.settlement = new SettlementPhase(deps, this::active);
        this.battle = new BattlePhase(deps, this::active, settlement);
        this.claiming = new ClaimingPhase(deps, this::active, battle, new Random());
        this.lobby = new LobbyPhase(deps, claiming);
    }

    private Behavior<RoomCommand> active(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        return Behaviors.receive(RoomCommand.class)
            .onMessage(RoomCommand.Join.class, m -> onRejoin(ctx, timers, state, m))
            .onMessage(RoomCommand.TimerTick.class, m -> onTimer(ctx, timers, state, m.key()))
            .onMessage(RoomCommand.SubmitEstimation.class, m -> onSubmitEstimation(ctx, timers, state, m))
            .onMessage(RoomCommand.SubmitAnswer.class, m -> battle.onSubmitAnswer(ctx, timers, state, m))
            .onMessage(RoomCommand.ClaimRegion.class, m -> claiming.onClaimRegion(ctx, timers, state, m))
            .onMessage(RoomCommand.Attack.class, m -> battle.onAttack(ctx, timers, state, m))
            .onMessage(RoomCommand.LeaveRoom.class, m -> onLeaveActive(ctx, timers, state, m.uid()))
            .onMessage(RoomCommand.SessionTerminated.class, m -> onLeaveActive(ctx, timers, state, m.uid()))
            .build();
    }

    private Behavior<RoomCommand> onRejoin(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            RoomCommand.Join m) {

        if (m.session() == null) {
            return active(ctx, timers, state);
        }
        var newState = state.withSession(m.uid(), m.session());
        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, newState)));
        return active(ctx, timers, newState);
    }

    private Behavior<RoomCommand> onSubmitEstimation(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            RoomCommand.SubmitEstimation m) {

        if (RealtimeConstants.P_TIEBREAKER.equals(state.phase())) {
            return battle.onSubmitEstimationTiebreak(ctx, timers, state, m);
        }
        return claiming.onSubmitEstimation(ctx, timers, state, m);
    }

    private Behavior<RoomCommand> onLeaveActive(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            String leaverUid) {

        timers.cancel("phase-timer");
        timers.cancel("pick-timer");
        timers.cancel("duel-timer");
        timers.cancel("tiebreak-timer");

        var stateWithout = state.withoutSession(leaverUid);

        String winner = stateWithout.players().keySet().stream()
            .filter(uid -> !uid.equals(leaverUid))
            .findFirst()
            .orElse(null);

        if (winner == null) {
            return Behaviors.stopped();
        }

        RoomSessionHub.broadcast(stateWithout, new RoomCommand.RoomEvent.Event(
            RealtimeConstants.E_LEAVE_ROOM,
            Map.of("roomId", state.id(), "reason", "opponent_left", "winnerUid", winner)));

        return settlement.finishGame(ctx, timers, stateWithout, winner, "opponent_left");
    }

    private Behavior<RoomCommand> onTimer(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            String key) {

        return switch (key) {
            case "resolve_claim_q" -> claiming.resolveClaimQ(ctx, timers, state);
            case "claim_rank_margin" -> claiming.startClaimPick(ctx, timers, state);
            case "auto_pick" -> claiming.autoPick(ctx, timers, state);
            case "duel_timeout" -> battle.resolveDuel(ctx, timers, state, true);
            case "tiebreak_timeout" -> battle.resolveTiebreak(ctx, timers, state, true);
            case "tiebreak_reveal_margin" -> battle.finishTiebreakAfterReveal(ctx, timers, state);
            case "victory_cinematic" -> settlement.emitSettlement(ctx, timers, state);
            case "room_teardown" -> Behaviors.stopped();
            default -> active(ctx, timers, state);
        };
    }
}
