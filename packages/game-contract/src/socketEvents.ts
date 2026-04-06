/**
 * Socket.IO event names — default namespace `/`.
 * Payload shapes are documented in docs/SOCKET_PROTOCOL.md.
 */

export const CLIENT_TO_SERVER_EVENTS = [
  "leave_matchmaking",
  "join_matchmaking",
  "start_match",
  "expansion_submit_number",
  "expansion_pick_hex",
  "room_chat",
  "attack",
  "submit_answer",
  "tiebreaker_vote",
  "tiebreaker_minefield_place",
  "tiebreaker_minefield_step",
  "tiebreaker_rhythm_submit",
  "tiebreaker_rps_submit",
  "tiebreaker_closest_submit",
  "use_powerup",
] as const;

export type ClientToServerEvent = (typeof CLIENT_TO_SERVER_EVENTS)[number];

export const SERVER_TO_CLIENT_EVENTS = [
  "room_update",
  "game_start",
  "join_rejected",
  "expansion_pick_phase",
  "expansion_pick_invalid",
  "expansion_round_start",
  "expansion_complete",
  "room_chat",
  "attack_blocked",
  "attack_invalid",
  "duel_start",
  "duel_resolved",
  "duel_options_update",
  "duel_audience_hint",
  "duel_safety_locked",
  "duel_spyglass_hint",
  "tiebreaker_started",
  "tiebreaker_game_start",
  "tiebreaker_pick_result",
  "tiebreaker_minefield_ready",
  "tiebreaker_rhythm_error",
  "tiebreaker_rhythm_next",
  "tiebreaker_rps_round",
  "tiebreaker_closest_reveal",
  "game_ended",
] as const;

export type ServerToClientEvent = (typeof SERVER_TO_CLIENT_EVENTS)[number];
