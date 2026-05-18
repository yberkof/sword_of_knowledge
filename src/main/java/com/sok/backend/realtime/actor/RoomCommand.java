package com.sok.backend.realtime.actor;

import com.sok.backend.realtime.domain.GameState;
import org.apache.pekko.actor.typed.ActorRef;
import java.io.Serializable;

/**
 * Clean, production-grade protocol for the GameRoomActor.
 */
public interface RoomCommand extends Serializable {

    // --- Client Inbound Commands ---
    record Join(String uid, String name, String avatar, ActorRef<RoomEvent> session) implements RoomCommand {}
    record StartMatch(String uid) implements RoomCommand {}
    record ClaimRegion(String uid, int regionId) implements RoomCommand {}
    record Attack(String attackerUid, int targetHexId) implements RoomCommand {}
    record SubmitEstimation(String uid, int value, long timestamp) implements RoomCommand {}
    record SubmitAnswer(String uid, int answerIndex, long timestamp) implements RoomCommand {}
    record LeaveRoom(String uid) implements RoomCommand {}

    // --- Internal Timers & Lifecycle ---
    record TimerTick(String key) implements RoomCommand {}
    record SessionTerminated(String uid) implements RoomCommand {}

    /**
     * Events emitted BY the Room Actor to sessions.
     */
    @com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME, property = "type")
    @com.fasterxml.jackson.annotation.JsonSubTypes({
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = RoomEvent.RoomUpdate.class, name = "room_update"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = RoomEvent.Event.class, name = "event"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = RoomEvent.JoinRejected.class, name = "join_rejected")
    })
    interface RoomEvent extends Serializable {
        record RoomUpdate(java.util.Map<String, Object> state) implements RoomEvent {}
        record Event(String name, Object payload) implements RoomEvent {}
        record JoinRejected(String reason) implements RoomEvent {}
    }
}
