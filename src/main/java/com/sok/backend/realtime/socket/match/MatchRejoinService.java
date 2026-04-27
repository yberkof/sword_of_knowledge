package com.sok.backend.realtime.socket.match;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.persistence.RoomRehydrationService;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.room.RoomSerialCommandService;
import com.sok.backend.realtime.room.RoomStore;
import com.sok.backend.persistence.ActiveRoomRepository;
import org.springframework.stereotype.Component;

/**
 * Reconnect path for a uid already in a room (moved from {@code SocketGateway}).
 */
@Component
public class MatchRejoinService {

  public enum RejoinResult {
    NO_EXISTING_ROOM,
    REJOIN_QUEUED,
    SERVER_BUSY
  }

  private final RoomStore store;
  private final ActiveRoomRepository activeRoomRepository;
  private final RoomRehydrationService roomRehydrationService;
  private final RoomSerialCommandService roomCommands;
  private final RoomBroadcaster broadcaster;

  public MatchRejoinService(
      RoomStore store,
      ActiveRoomRepository activeRoomRepository,
      RoomRehydrationService roomRehydrationService,
      RoomSerialCommandService roomCommands,
      RoomBroadcaster broadcaster) {
    this.store = store;
    this.activeRoomRepository = activeRoomRepository;
    this.roomRehydrationService = roomRehydrationService;
    this.roomCommands = roomCommands;
    this.broadcaster = broadcaster;
  }

  public RejoinResult rejoinIfApplicable(
      SocketIOClient client, String uid, SocketIOServer server, String avatarUrl) {
    String resolved = store.roomIdForUid(uid);
    if (resolved == null) {
      resolved = activeRoomRepository.findRoomIdByUid(uid).orElse(null);
    }
    if (resolved != null && store.get(resolved) == null) {
      roomRehydrationService.hydrateRoomFromDbIfAbsent(resolved);
    }
    final String existingRoomId = store.roomIdForUid(uid);
    if (existingRoomId == null) {
      return RejoinResult.NO_EXISTING_ROOM;
    }
    if (!roomCommands.runWithServer(
        existingRoomId,
        server,
        (s, room) -> {
          PlayerState p = room.playersByUid.get(uid);
          if (p != null) {
            p.socketId = client.getSessionId().toString();
            p.online = true;
            p.lastSeenAt = System.currentTimeMillis();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
              p.avatarUrl = avatarUrl;
            }
          }
          client.joinRoom(existingRoomId);
          broadcaster.emitRoomUpdate(room);
        })) {
      return RejoinResult.SERVER_BUSY;
    }
    return RejoinResult.REJOIN_QUEUED;
  }
}
