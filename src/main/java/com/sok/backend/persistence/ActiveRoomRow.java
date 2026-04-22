package com.sok.backend.persistence;

import java.time.Instant;
import java.util.List;

/** One row from {@code active_rooms} for rehydration. */
public record ActiveRoomRow(
    String roomId,
    String phase,
    List<String> playerUids,
    String inviteCode,
    String snapshotJson,
    String hostUid,
    String matchMode,
    String rulesetId,
    String mapId,
    Instant matchStartedAt,
    Instant updatedAt,
    long version) {}
