package com.sok.backend.realtime.actor;

import com.sok.backend.realtime.domain.GameState;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

/**
 * Supplies the active-match {@link Behavior} for phase handlers that transition back to gameplay.
 */
@FunctionalInterface
public interface RoomBehaviorFactory {

    Behavior<RoomCommand> active(
            ActorContext<RoomCommand> ctx,
            TimerScheduler<RoomCommand> timers,
            GameState state);
}
