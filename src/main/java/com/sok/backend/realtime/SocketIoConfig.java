package com.sok.backend.realtime;

import com.corundumstudio.socketio.AuthorizationListener;
import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketIOServer;
import com.sok.backend.service.AuthTokenService;
import com.sok.backend.service.RateLimitService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.socket.enabled", havingValue = "true", matchIfMissing = true)
public class SocketIoConfig implements InitializingBean, DisposableBean {
  private final SocketIOServer socketServer;

  public SocketIoConfig(
      @Value("${app.socket.host}") String host,
      @Value("${app.socket.port}") int port,
      @Value("${app.cors-origins:http://localhost:5173,http://127.0.0.1:5173}") String corsOrigins,
      @Value("${app.socket.max-conn-per-minute:60}") int maxConnPerMinute,
      @Value("${app.socket.allow-insecure:false}") boolean allowInsecureSocket,
      AuthTokenService authTokenService,
      RateLimitService rateLimitService,
      SocketGateway socketGateway) {
    final List<String> allowedOrigins = new ArrayList<String>();
    for (String o : corsOrigins.split(",")) {
      if (!o.trim().isEmpty()) allowedOrigins.add(o.trim());
    }
    Configuration config = new Configuration();
    config.setHostname(host);
    config.setPort(port);
    config.setOrigin(corsOrigins);
    config.setContext("/socket.io");
    config.setAuthorizationListener(
        new AuthorizationListener() {
          @Override
          public AuthorizationResult getAuthorizationResult(HandshakeData data) {
            String ip = data.getAddress() == null ? "unknown" : String.valueOf(data.getAddress());
            if (!rateLimitService.allow("socket:handshake:" + ip, maxConnPerMinute, 60_000L)) {
              return AuthorizationResult.FAILED_AUTHORIZATION;
            }
            if (!allowedOrigins.isEmpty()) {
              String origin = data.getHttpHeaders().get("Origin");
              if (origin != null && !allowedOrigins.contains(origin)) {
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
    socketServer.start();
  }

  @Override
  public void destroy() {
    socketServer.stop();
  }
}
