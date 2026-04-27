package com.sok.backend.realtime;

import com.corundumstudio.socketio.*;
import com.sok.backend.service.AuthTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
@ConditionalOnProperty(name = "app.socket.enabled", havingValue = "true", matchIfMissing = true)
public class SocketIoConfig implements DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(SocketIoConfig.class);
  private final SocketIOServer socketServer;

  public SocketIoConfig(
      @Value("${app.socket.host}") String host,
      @Value("${app.socket.port}") int port,
      @Value("${app.socket.allow-insecure:false}") boolean allowInsecureSocket,
      AuthTokenService authTokenService) {
    Configuration config = new Configuration();
    config.setHostname(host);
    config.setPort(port);
    SocketConfig socketConfig = config.getSocketConfig();
    if (socketConfig == null) {
      socketConfig = new SocketConfig();
      config.setSocketConfig(socketConfig);
    }
    socketConfig.setReuseAddress(true);
    // Browser CORS for the Socket.IO handshake: allow any origin (no Origin allowlist).
    config.setOrigin("*");
    config.setContext("/socket.io");
    config.setAuthorizationListener(
        new AuthorizationListener() {
          @Override
          public AuthorizationResult getAuthorizationResult(HandshakeData data) {
            if (allowInsecureSocket) {
              return AuthorizationResult.SUCCESSFUL_AUTHORIZATION;
            }
            String token = data.getSingleUrlParam("token");
            if (token == null || token.trim().isEmpty()) {
              return AuthorizationResult.FAILED_AUTHORIZATION;
            }
            try {
              authTokenService.verifyAccessToken(token);
              return AuthorizationResult.SUCCESSFUL_AUTHORIZATION;
            } catch (Exception ex) {
              return AuthorizationResult.FAILED_AUTHORIZATION;
            }
          }
        });
    this.socketServer = new SocketIOServer(config);
    // Server listen + handler registration: see {@link SocketGateway#registerHandlersAfterSocketBeanReady}.
  }

  /**
   * Starts accept after {@link SocketGateway} has registered all event listeners (order matters for
   * incoming connections).
   */
  public static void startListening(SocketIOServer socketServer) {
    try {
      socketServer.start();
      Configuration cfg = socketServer.getConfiguration();
      log.info(
          "Srf Socket.IO listening on {}:{} (path {}). Tail THIS process for duel/claim logs, not a separate API-only JVM.",
          cfg.getHostname(),
          cfg.getPort(),
          cfg.getContext());
    } catch (Exception e) {
      for (Throwable t = e; t != null; t = t.getCause()) {
        if (t instanceof java.net.BindException) {
          int port = socketServer.getConfiguration().getPort();
          throw new IllegalStateException(
              "Socket.IO cannot bind port "
                  + port
                  + " (app.socket.port / SOCKET_PORT). Another process is listening, or a stale JVM is still bound. "
                  + "Find it: `ss -tlnp | grep ':"
                  + port
                  + "'` or `lsof -iTCP:"
                  + port
                  + " -sTCP:LISTEN`. Kill: `fuser -k "
                  + port
                  + "/tcp`. Or use a free port: `SOCKET_PORT=18081` (and point the game at that port).",
              e);
        }
      }
      throw e;
    }
  }

  /** Exposes the Netty Socket.IO server as a Spring bean (e.g. {@code RoomRehydrationService}). */
  @Bean
  public SocketIOServer socketIOServer() {
    return socketServer;
  }

  @Override
  public void destroy() {
    socketServer.stop();
  }
}
