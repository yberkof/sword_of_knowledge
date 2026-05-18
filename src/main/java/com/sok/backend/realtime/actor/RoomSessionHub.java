package com.sok.backend.realtime.actor;

import com.sok.backend.realtime.domain.GameState;

/**
 * Fan-out room events to registered WebSocket session adapters.
 */
public final class RoomSessionHub {

    private RoomSessionHub() {}

    public static void broadcast(GameState state, RoomCommand.RoomEvent event) {
        state.sessions().values().forEach(ref -> ref.tell(event));
    }
}
