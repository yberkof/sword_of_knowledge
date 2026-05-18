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
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

import java.time.Duration;
import java.util.*;

/**
 * End-of-match evaluation and settlement timers.
 */
public final class SettlementPhase {

    private final RoomDeps deps;
    private final RoomBehaviorFactory behaviors;

    public SettlementPhase(RoomDeps deps, RoomBehaviorFactory behaviors) {
        this.deps = deps;
        this.behaviors = behaviors;
    }

    public Behavior<RoomCommand> evaluateEnd(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        long alive = state.players().values().stream().filter(p -> !p.isEliminated()).count();
        if (alive <= 1) {
            String winner = state.players().values().stream()
                .filter(p -> !p.isEliminated())
                .map(GameState.Player::uid)
                .findFirst()
                .orElse(null);
            return finishGame(ctx, timers, state, winner, "domination");
        }
        if (state.round() >= RoomHelpers.cfg(deps).getMaxRounds()) {
            String winner = state.players().values().stream()
                .max(Comparator.comparingInt(GameState.Player::score))
                .map(GameState.Player::uid)
                .orElse(null);
            return finishGame(ctx, timers, state, winner, "threshold");
        }
        return null;
    }

    public Behavior<RoomCommand> finishGame(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            String winnerUid,
            String reason) {

        var ended = new GameState(
            state.id(), RealtimeConstants.P_ENDED, state.hostUid(), state.round(), state.currentTurnIndex(),
            System.currentTimeMillis(), null, state.players(), state.regions(),
            null, List.of(), Map.of(), Map.of(), null, null, state.sessions()
        );

        var sorted = state.players().values().stream()
            .sorted(Comparator.comparingInt(GameState.Player::score).reversed())
            .toList();
        List<Map<String, Object>> rankings = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            rankings.add(Map.of("uid", sorted.get(i).uid(), "place", i + 1));
        }

        RoomSessionHub.broadcast(ended, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, ended)));
        RoomSessionHub.broadcast(ended, new RoomCommand.RoomEvent.Event(
            RealtimeConstants.E_GAME_ENDED,
            Map.of("winnerUid", winnerUid != null ? winnerUid : "", "reason", reason, "rankings", rankings,
                "room", RoomHelpers.clientSummary(deps, ended))));

        long victoryMs = "opponent_left".equals(reason)
            ? 1500
            : RoomHelpers.cfg(deps).getVictoryCinematicMs();
        timers.startSingleTimer("victory-timer",
            new RoomCommand.TimerTick("victory_cinematic"), Duration.ofMillis(victoryMs));
        return behaviors.active(ctx, timers, ended);
    }

    public Behavior<RoomCommand> emitSettlement(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        RoomSessionHub.broadcast(state, new RoomCommand.RoomEvent.Event(
            RealtimeConstants.E_ACHIEVEMENT,
            Map.of("progress", Map.of(), "missionsCompleted", List.of())));

        RoomSessionHub.broadcast(state, new RoomCommand.RoomEvent.Event(
            RealtimeConstants.E_LEAVE_ROOM,
            Map.of("roomId", state.id(), "reason", "match_finished")));

        timers.startSingleTimer("teardown-timer",
            new RoomCommand.TimerTick("room_teardown"), Duration.ofSeconds(RoomHelpers.cfg(deps).getReconnectGraceSeconds()));
        return behaviors.active(ctx, timers, state);
    }
}
