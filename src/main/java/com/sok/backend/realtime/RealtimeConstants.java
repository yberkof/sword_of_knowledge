package com.sok.backend.realtime;

/**
 * Single Source of Truth for all Realtime Strings.
 */
public final class RealtimeConstants {
    // Phases
    public static final String P_WAITING = "waiting";
    public static final String P_CLAIM_Q = "claiming_question";
    public static final String P_CLAIM_P = "claiming_pick";
    public static final String P_BATTLE = "battle";
    public static final String P_DUEL = "duel";
    public static final String P_TIEBREAKER = "battle_tiebreaker";
    public static final String P_ENDED = "ended";

    // S2C Events
    public static final String E_ROOM_UPDATE = "room_update";
    public static final String E_PHASE_CHANGED = "phase_changed";
    public static final String E_ESTIMATION_QUESTION = "estimation_question";
    public static final String E_ANSWER_EVENT = "answer_event";
    public static final String E_CLAIM_RANKINGS = "claim_rankings";
    public static final String E_DUEL_START = "duel_start";
    public static final String E_DUEL_RESOLVED = "duel_resolved";
    public static final String E_TIEBREAKER_START = "battle_tiebreaker_start";
    public static final String E_GAME_ENDED = "game_ended";
    public static final String E_ACHIEVEMENT = "achievement_event";
    public static final String E_LEAVE_ROOM = "leave_room";
    public static final String E_ATTACK_INVALID = "attack_invalid";
    public static final String E_ERROR = "error_event";

    private RealtimeConstants() {}
}
