package com.sok.backend.realtime;

import com.corundumstudio.socketio.AuthorizationListener;
import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.config.DevOriginUtil;
import com.sok.backend.service.AuthTokenService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "app.socket.enabled", havingValue = "true", matchIfMissing = true)
public class SocketIoConfig implements InitializingBean, DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(SocketIoConfig.class);
  private final SocketIOServer socketServer;

  /** Same idea as HTTP CORS: Next (3000) + Vite (5173) on localhost and 127.0.0.1. */
  private static List<String> socketAllowedOrigins(String corsOriginsCsv) {
    LinkedHashSet<String> set = new LinkedHashSet<String>();
    for (String o : corsOriginsCsv.split(",")) {
      String t = o.trim();
      if (!t.isEmpty()) {
        set.add(t);
      }
    }
    set.add("http://localhost:3000");
    set.add("http://127.0.0.1:3000");
    set.add("http://localhost:5173");
    set.add("http://127.0.0.1:5173");
    return new ArrayList<String>(set);
  }

  public SocketIoConfig(
      @Value("${app.socket.host}") String host,
      @Value("${app.socket.port}") int port,
      @Value("${app.cors-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000}")
          String corsOrigins,
      @Value("${app.socket.allow-insecure:false}") boolean allowInsecureSocket,
      AuthTokenService authTokenService,
      SocketGateway socketGateway) {
    final List<String> allowedOrigins = socketAllowedOrigins(corsOrigins);
    Configuration config = new Configuration();
    config.setHostname(host);
    config.setPort(port);
    SocketConfig socketConfig = config.getSocketConfig();
    if (socketConfig == null) {
      socketConfig = new SocketConfig();
      config.setSocketConfig(socketConfig);
    }
    socketConfig.setReuseAddress(true);
    // netty-socketio sets this string verbatim as `Access-Control-Allow-Origin`; a comma-separated
    // list is invalid (browser expects one origin or `*`). Real allowlist is enforced below.
    config.setOrigin("*");
    config.setContext("/socket.io");
    config.setAuthorizationListener(
        new AuthorizationListener() {
          @Override
          public AuthorizationResult getAuthorizationResult(HandshakeData data) {
            if (!allowedOrigins.isEmpty()) {
              String origin = data.getHttpHeaders().get("Origin");
              if (origin != null
                  && !allowedOrigins.contains(origin)
                  && !DevOriginUtil.isLocalPrivateDevOrigin(origin)) {
                return AuthorizationResult.FAILED_AUTHORIZATION;
              }
            }
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
    socketGateway.register(socketServer, authTokenService, allowInsecureSocket);
  }

  @Override
  public void afterPropertiesSet() {
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

  @Override
  public void destroy() {
    socketServer.stop();
  }
}
