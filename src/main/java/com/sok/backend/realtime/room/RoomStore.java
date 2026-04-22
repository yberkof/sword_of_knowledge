package com.sok.backend.realtime.room;

import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.persistence.RoomSnapshotCoordinator;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * In-memory index of live rooms plus the uid→roomId reverse lookup used by disconnect handling and
 * matchmaking. Kept tiny on purpose: mutation policy lives with the caller (phase orchestrators,
 * matchmaking allocator) so invariants are enforced per call-site.
 */
@Component
public class RoomStore implements DisposableBean {
  private final Map<String, RoomState> rooms = new ConcurrentHashMap<>();
  private final Map<String, String> uidToRoom = new ConcurrentHashMap<>();
  private final AtomicLong roomSeq = new AtomicLong(1L);
  private final RoomSnapshotCoordinator snapshotCoordinator;

  public RoomStore(RoomSnapshotCoordinator snapshotCoordinator) {
    this.snapshotCoordinator = snapshotCoordinator;
  }

  public Map<String, RoomState> rooms() {
    return rooms;
  }

  public RoomState get(String roomId) {
    return rooms.get(roomId);
  }

  public void put(String roomId, RoomState room) {
    rooms.put(roomId, room);
  }

  public RoomState remove(String roomId) {
    return rooms.remove(roomId);
  }

  public int size() {
    return rooms.size();
  }

  public Collection<RoomState> values() {
    return rooms.values();
  }

  public String roomIdForUid(String uid) {
    return uidToRoom.get(uid);
  }

  public void mapUidToRoom(String uid, String roomId) {
    uidToRoom.put(uid, roomId);
  }

  public void unmapUid(String uid) {
    uidToRoom.remove(uid);
  }

  public String newRoomId() {
    return "room_" + roomSeq.getAndIncrement();
  }

  @Override
  public void destroy() {
    for (RoomState room : rooms.values()) {
      snapshotCoordinator.snapshotDurable(room);
    }
  }
}
