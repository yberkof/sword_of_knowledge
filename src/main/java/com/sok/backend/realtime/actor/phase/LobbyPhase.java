package com.sok.backend.realtime.actor.phase;

import com.sok.backend.realtime.RealtimeConstants;
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
 * Waiting lobby: join, leave, start match.
 */
public final class LobbyPhase {

    private final RoomDeps deps;
    private final ClaimingPhase claiming;

    public LobbyPhase(RoomDeps deps, ClaimingPhase claiming) {
        this.deps = deps;
        this.claiming = claiming;
    }

    public Behavior<RoomCommand> waiting(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        return Behaviors.receive(RoomCommand.class)
            .onMessage(RoomCommand.Join.class, m -> onJoin(ctx, timers, state, m))
            .onMessage(RoomCommand.StartMatch.class, m -> startMatch(ctx, timers, state))
            .onMessage(RoomCommand.LeaveRoom.class, m -> onLeaveWaiting(ctx, timers, state, m.uid()))
            .onMessage(RoomCommand.SessionTerminated.class, m -> onLeaveWaiting(ctx, timers, state, m.uid()))
            .build();
    }

    private Behavior<RoomCommand> onJoin(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            RoomCommand.Join m) {

        if (m.session() == null) {
            return waiting(ctx, timers, state);
        }

        var registered = state.withSession(m.uid(), m.session());
        var newPlayers = new HashMap<>(registered.players());
        var p = new GameState.Player(
            m.uid(), m.name(),
            List.of("red", "blue", "green", "yellow", "purple", "orange").get(registered.players().size() % 6),
            m.avatar(), true, RoomHelpers.cfg(deps).getInitialCastleHp(), 0, null, false
        );
        newPlayers.put(m.uid(), p);

        String hostUid = registered.hostUid() == null ? m.uid() : registered.hostUid();
        var newState = RoomHelpers.withPlayers(registered, newPlayers, hostUid);
        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, newState)));

        if (newState.players().size() >= RoomHelpers.cfg(deps).getMinPlayers()) {
            timers.startSingleTimer("start-match", new RoomCommand.StartMatch(hostUid), Duration.ofSeconds(2));
        }

        return waiting(ctx, timers, newState);
    }

    public Behavior<RoomCommand> onLeaveWaiting(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state,
            String uid) {

        var players = new HashMap<>(state.players());
        players.remove(uid);
        if (players.isEmpty()) {
            return Behaviors.stopped();
        }
        String hostUid = uid.equals(state.hostUid())
            ? players.keySet().iterator().next()
            : state.hostUid();
        var newState = RoomHelpers.withPlayers(state.withoutSession(uid), players, hostUid);
        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, newState)));
        return waiting(ctx, timers, newState);
    }

    public Behavior<RoomCommand> startMatch(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state) {

        var regions = RoomHelpers.buildBasic1v1Regions(deps);
        var players = new HashMap<>(state.players());
        var castleSlots = RoomHelpers.cfg(deps).getCastleIndices();
        int slot = 0;
        for (var p : players.values()) {
            int rId = castleSlots.get(slot++ % castleSlots.size());
            var reg = regions.get(rId);
            regions.put(rId, new GameState.Region(rId, p.uid(), true, reg.neighbors()));
            players.put(p.uid(), new GameState.Player(
                p.uid(), p.name(), p.color(), p.avatarUrl(), p.online(), p.castleHp(), 0, rId, false));
        }

        var newState = new GameState(
            state.id(), RealtimeConstants.P_WAITING, state.hostUid(), 1, 0,
            System.currentTimeMillis(), null,
            Collections.unmodifiableMap(players), Collections.unmodifiableMap(regions),
            null, List.of(), Map.of(), Map.of(), null, null, state.sessions()
        );

        RoomSessionHub.broadcast(newState, new RoomCommand.RoomEvent.RoomUpdate(RoomHelpers.clientSummary(deps, newState)));
        return claiming.startClaimQ(ctx, timers, newState);
    }
}
