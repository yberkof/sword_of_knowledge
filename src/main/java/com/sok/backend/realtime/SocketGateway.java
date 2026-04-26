package com.sok.backend.realtime;

import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.realtime.binding.SocketEventBinder;
import com.sok.backend.realtime.room.RoomExecutorRegistry;
import com.sok.backend.realtime.room.RoomLifecycle;
import com.sok.backend.realtime.room.RoomStore;
import com.sok.backend.realtime.room.RoomBroadcaster;
import com.sok.backend.realtime.socket.SocketConnectHandshakeService;
import com.sok.backend.realtime.socket.SocketRoomBackgroundService;
import com.sok.backend.realtime.socket.match.OnlinePlayerCountService;
import com.sok.backend.service.AuthTokenService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Composition root for Socket.IO: connect auth, domain {@link SocketEventBinder}s, background
 * ticks. Event logic lives in {@code com.sok.backend.realtime.socket} handlers and binders.
 */
@Component
public class SocketGateway implements DisposableBean {

  private final RoomBroadcaster broadcaster;
  private final SocketConnectHandshakeService connectHandshake;
  private final List<SocketEventBinder> eventBinders;
  private final SocketRoomBackgroundService roomBackground;
  private final RoomStore store;
  private final RoomLifecycle lifecycle;
  private final RoomExecutorRegistry roomExecutors;
  private final OnlinePlayerCountService onlinePlayerCountService;

  public SocketGateway(
      RoomBroadcaster broadcaster,
      SocketConnectHandshakeService connectHandshake,
      List<SocketEventBinder> eventBinders,
      SocketRoomBackgroundService roomBackground,
      RoomStore store,
      RoomLifecycle lifecycle,
      RoomExecutorRegistry roomExecutors,
      OnlinePlayerCountService onlinePlayerCountService) {
    this.broadcaster = broadcaster;
    this.connectHandshake = connectHandshake;
    this.eventBinders = eventBinders;
    this.roomBackground = roomBackground;
    this.store = store;
    this.lifecycle = lifecycle;
    this.roomExecutors = roomExecutors;
    this.onlinePlayerCountService = onlinePlayerCountService;
  }

  public void register(
      SocketIOServer server, AuthTokenService authTokenService, boolean allowInsecureSocket) {
    broadcaster.attach(server);
    connectHandshake.register(server, authTokenService, allowInsecureSocket);
    for (SocketEventBinder binder : eventBinders) {
      binder.bind(server);
    }
    roomBackground.register(server);
  }

  public int roomCount() {
    return store.size();
  }

  public int roomExecutorCount() {
    return roomExecutors.activeWorkerCount();
  }

  public int roomWorkerQueueDepth() {
    return roomExecutors.queueDepth();
  }

  public boolean isHealthy() {
    return roomExecutors.hasQueueHeadroom();
  }

  public int currentOnlinePlayers() {
    return onlinePlayerCountService.currentOnline();
  }

  @Override
  public void destroy() {
    for (String roomId : new ArrayList<String>(store.rooms().keySet())) {
      lifecycle.shutdownRoom(roomId);
    }
  }
}
