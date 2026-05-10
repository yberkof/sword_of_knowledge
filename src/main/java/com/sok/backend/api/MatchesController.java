package com.sok.backend.api;

import com.sok.backend.persistence.ActiveRoomRepository;
import com.sok.backend.persistence.MatchHistoryRepository;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.persistence.RoomRehydrationService;
import com.sok.backend.realtime.room.RoomStore;
import com.sok.backend.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API for in-progress match discovery (e.g. client refresh / re-login). Source of truth
 * remains the in-memory {@link RoomStore} with optional rehydration from {@code active_rooms}.
 */
@RestController
@RequestMapping("/api/matches")
public class MatchesController {

  private final RoomStore roomStore;
  private final ActiveRoomRepository activeRoomRepository;
  private final RoomRehydrationService rehydrationService;
  private final MatchHistoryRepository matchHistoryRepository;

  public MatchesController(
      RoomStore roomStore,
      ActiveRoomRepository activeRoomRepository,
      RoomRehydrationService rehydrationService,
      MatchHistoryRepository matchHistoryRepository) {
    this.roomStore = roomStore;
    this.activeRoomRepository = activeRoomRepository;
    this.rehydrationService = rehydrationService;
    this.matchHistoryRepository = matchHistoryRepository;
  }

  @GetMapping("/active")
  public ResponseEntity<ActiveMatchResponse> getActive() {
    String uid = SecurityUtils.currentUid();
    String roomId = roomStore.roomIdForUid(uid);
    if (roomId == null) {
      roomId = activeRoomRepository.findRoomIdByUid(uid).orElse(null);
    }
    if (roomId == null) {
      return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    RoomState room = roomStore.get(roomId);
    if (room == null) {
      rehydrationService.hydrateRoomFromDbIfAbsent(roomId);
      room = roomStore.get(roomId);
    }
    if (room == null) {
      return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    if (RoomState.PHASE_ENDED.equals(room.phase)) {
      return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    return ResponseEntity.ok(
        new ActiveMatchResponse(room.id, room.phase, room.mapId == null ? "" : room.mapId));
  }

  @GetMapping("/history")
  public ResponseEntity<?> getHistory(
      @RequestParam(defaultValue = "10") int limit, @RequestParam(defaultValue = "0") int offset) {
    String uid = SecurityUtils.currentUid();
    return ResponseEntity.ok(matchHistoryRepository.getMatchHistory(uid, limit, offset));
  }

  public record ActiveMatchResponse(String matchId, String phase, String mapId) {}
}
