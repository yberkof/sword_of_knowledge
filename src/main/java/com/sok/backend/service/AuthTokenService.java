package com.sok.backend.service;

import com.sok.backend.api.UnauthorizedException;
import com.sok.backend.persistence.AuthSessionRecord;
import com.sok.backend.persistence.AuthSessionRepository;
import com.sok.backend.persistence.UserRepository;
import com.sok.backend.security.LocalJwtService;
import com.sok.backend.security.RefreshTokenHasher;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {
  private final LocalJwtService localJwtService;
  private final RefreshTokenHasher refreshTokenHasher;
  private final AuthSessionRepository authSessionRepository;
  private final UserRepository userRepository;

  public AuthTokenService(
      LocalJwtService localJwtService,
      RefreshTokenHasher refreshTokenHasher,
      AuthSessionRepository authSessionRepository,
      UserRepository userRepository) {
    this.localJwtService = localJwtService;
    this.refreshTokenHasher = refreshTokenHasher;
    this.authSessionRepository = authSessionRepository;
    this.userRepository = userRepository;
  }

  public TokenPair issueForUser(String userId, String deviceInfo, String ip) {
    UUID sid = UUID.randomUUID();
    String refreshToken = localJwtService.issueRefreshToken(userId, sid);
    String refreshHash = refreshTokenHasher.sha256(refreshToken);
    LocalJwtService.LocalClaims refreshClaims = localJwtService.verifyRefreshToken(refreshToken);
    AuthSessionRecord row =
        authSessionRepository.create(
            sid,
            userId,
            refreshHash,
            OffsetDateTime.ofInstant(Instant.ofEpochSecond(refreshClaims.expEpochSeconds()), ZoneOffset.UTC),
            deviceInfo,
            ip);
    String accessToken = localJwtService.issueAccessToken(userId, row.id());
    userRepository.touchLogin(userId);
    return new TokenPair(accessToken, refreshToken);
  }

  public TokenPair rotateRefreshToken(String refreshToken, String deviceInfo, String ip) {
    LocalJwtService.LocalClaims claims;
    try {
      claims = localJwtService.verifyRefreshToken(refreshToken);
    } catch (IllegalStateException ex) {
      throw new UnauthorizedException("unauthorized");
    }
    Optional<AuthSessionRecord> byId = authSessionRepository.findById(claims.sessionId());
    if (!byId.isPresent()) throw new UnauthorizedException("unauthorized");
    AuthSessionRecord session = byId.get();
    if (session.revokedAt() != null || session.expiresAt() == null || session.expiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
      throw new UnauthorizedException("unauthorized");
    }
    String incomingHash = refreshTokenHasher.sha256(refreshToken);
    if (!incomingHash.equals(session.refreshHash())) {
      authSessionRepository.revokeAllByUser(session.userId());
      throw new UnauthorizedException("unauthorized");
    }

    String nextRefresh = localJwtService.issueRefreshToken(claims.userId(), claims.sessionId());
    String nextHash = refreshTokenHasher.sha256(nextRefresh);
    LocalJwtService.LocalClaims nextClaims = localJwtService.verifyRefreshToken(nextRefresh);
    int updated =
        authSessionRepository.rotate(
            claims.sessionId(),
            nextHash,
            OffsetDateTime.ofInstant(Instant.ofEpochSecond(nextClaims.expEpochSeconds()), ZoneOffset.UTC));
    if (updated <= 0) throw new UnauthorizedException("unauthorized");
    String accessToken = localJwtService.issueAccessToken(claims.userId(), claims.sessionId());
    return new TokenPair(accessToken, nextRefresh);
  }

  public void revokeByRefreshToken(String refreshToken) {
    LocalJwtService.LocalClaims claims;
    try {
      claims = localJwtService.verifyRefreshToken(refreshToken);
    } catch (IllegalStateException ex) {
      throw new UnauthorizedException("unauthorized");
    }
    authSessionRepository.revokeById(claims.sessionId());
  }

  public LocalJwtService.LocalClaims verifyAccessToken(String accessToken) {
    return localJwtService.verifyAccessToken(accessToken);
  }

  public static class TokenPair {
    private final String accessToken;
    private final String refreshToken;

    public TokenPair(String accessToken, String refreshToken) {
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
    }

    public String accessToken() { return accessToken; }
    public String refreshToken() { return refreshToken; }
  }
}
