package com.sok.backend.realtime.binding;

import com.corundumstudio.socketio.SocketIOServer;

/**
 * Registers Socket.IO event listeners for a focused domain (chat, matchmaking, etc.).
 *
 * <p>Implementations are discovered as Spring beans and installed by {@code SocketGateway.register}
 * at server start-up, keeping the gateway free of per-event wiring boilerplate.
 */
public interface SocketEventBinder {
  void bind(SocketIOServer server);
}
