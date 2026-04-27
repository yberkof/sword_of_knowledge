package com.sok.backend.realtime.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.service.AuthTokenService;
import org.springframework.stereotype.Component;

/**
 * Installs the authenticated uid (or dev guest) on the socket on connect.
 */
@Component
public class SocketConnectHandshakeService {

  public void register(SocketIOServer server, AuthTokenService authTokenService, boolean allowInsecureSocket) {
    server.addConnectListener(
        (SocketIOClient client) -> {
          String token = client.getHandshakeData().getSingleUrlParam("token");
          try {
            String uid = authTokenService.verifyAccessToken(token).userId();
            client.set("uid", uid);
          } catch (Exception ex) {
            if (allowInsecureSocket) {
              String uid = client.getHandshakeData().getSingleUrlParam("uid");
              client.set(
                  "uid", uid == null || uid.trim().isEmpty() ? "guest_" + client.getSessionId() : uid);
            } else {
              client.disconnect();
            }
          }
        });
  }
}
