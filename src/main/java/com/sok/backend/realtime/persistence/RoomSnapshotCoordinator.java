package com.sok.backend.realtime.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sok.backend.config.RealtimeScaleProperties;
import com.sok.backend.persistence.ActiveRoomRepository;
import com.sok.backend.realtime.RoomSnapshotPublisher;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RoomState;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Hot snapshots (Redis, TTL) + durable snapshots (Postgres). Call sites choose frequency: hot from
 * the realtime tick; durable on phase transitions only.
 */
@Component
public class RoomSnapshotCoordinator {

  private final RoomSnapshotMapper mapper;
  private final ActiveRoomRepository activeRooms;
  private final ObjectMapper objectMapper;
  private final RealtimeScaleProperties scale;
  private final ObjectProvider<RoomSnapshotPublisher> snapshotPublisher;

  public RoomSnapshotCoordinator(
      RoomSnapshotMapper mapper,
      ActiveRoomRepository activeRooms,
      ObjectMapper objectMapper,
      RealtimeScaleProperties scale,
      ObjectProvider<RoomSnapshotPublisher> snapshotPublisher) {
    this.mapper = mapper;
    this.activeRooms = activeRooms;
    this.objectMapper = objectMapper;
    this.scale = scale;
    this.snapshotPublisher = snapshotPublisher;
  }

  /** Redis mirror for cross-instance / ops (optional). */
  public void snapshotHot(RoomState room) {
    if (room == null || room.id == null) {
      return;
    }
    if (RoomState.PHASE_ENDED.equals(room.phase)) {
      return;
    }
    RoomSnapshotPublisher snap = snapshotPublisher.getIfAvailable();
    if (snap == null || !scale.isSnapshotToRedis()) {
      return;
    }
    snap.publish(room.id, mapper.toJson(room));
  }

  /** Postgres upsert — authoritative crash recovery. */
  public void snapshotDurable(RoomState room) {
    if (room == null || room.id == null) {
      return;
    }
    if (RoomState.PHASE_ENDED.equals(room.phase)) {
      return;
    }
    String snapshotJson = mapper.toJson(room);
    String playerUidsJson = playerUidsJson(room);
    Timestamp matchStarted =
        room.matchStartedAt > 0L ? new Timestamp(room.matchStartedAt) : null;
    activeRooms.upsert(
        room.id,
        room.phase,
        playerUidsJson,
        room.inviteCode,
        snapshotJson,
        room.hostUid,
        room.matchMode,
        room.rulesetId,
        room.mapId,
        matchStarted);
  }

  public void removeRoom(String roomId) {
    activeRooms.deleteById(roomId);
    RoomSnapshotPublisher snap = snapshotPublisher.getIfAvailable();
    if (snap != null) {
      snap.remove(roomId);
    }
  }

  private String playerUidsJson(RoomState room) {
    List<String> uids = new ArrayList<>();
    for (PlayerState p : room.players) {
      if (p.uid != null) {
        uids.add(p.uid);
      }
    }
    try {
      return objectMapper.writeValueAsString(uids);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("player_uids json", e);
    }
  }
}
