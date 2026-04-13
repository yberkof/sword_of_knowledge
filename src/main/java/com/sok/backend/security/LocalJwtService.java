package com.sok.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalJwtService {
  private final byte[] jwtSecret;
  private final String issuer;
  private final long accessTtlSeconds;
  private final long refreshTtlSeconds;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public LocalJwtService(
      @Value("${app.auth.jwt-secret:dev_change_me_local_jwt_secret_32_chars_min}") String jwtSecret,
      @Value("${app.auth.issuer:sok-local-auth}") String issuer,
      @Value("${app.auth.access-ttl-seconds:900}") long accessTtlSeconds,
      @Value("${app.auth.refresh-ttl-seconds:2592000}") long refreshTtlSeconds) {
    this.jwtSecret = jwtSecret.getBytes(StandardCharsets.UTF_8);
    this.issuer = issuer;
    this.accessTtlSeconds = accessTtlSeconds;
    this.refreshTtlSeconds = refreshTtlSeconds;
  }

  public String issueAccessToken(String userId, UUID sessionId) {
    return issueToken(userId, sessionId, "access", accessTtlSeconds);
  }

  public String issueRefreshToken(String userId, UUID sessionId) {
    return issueToken(userId, sessionId, "refresh", refreshTtlSeconds);
  }

  public LocalClaims verifyAccessToken(String token) {
    return parseTyped(token, "access");
  }

  public LocalClaims verifyRefreshToken(String token) {
    return parseTyped(token, "refresh");
  }

  private String issueToken(String userId, UUID sessionId, String type, long ttlSeconds) {
    Instant now = Instant.now();
    long iat = now.getEpochSecond();
    long exp = now.plusSeconds(ttlSeconds).getEpochSecond();
    try {
      Map<String, Object> header = new HashMap<>();
      header.put("alg", "HS256");
      header.put("typ", "JWT");

      Map<String, Object> payload = new HashMap<>();
      payload.put("sub", userId);
      payload.put("iss", issuer);
      payload.put("iat", iat);
      payload.put("exp", exp);
      payload.put("typ", type);
      payload.put("sid", sessionId.toString());

      String h = base64Url(objectMapper.writeValueAsBytes(header));
      String p = base64Url(objectMapper.writeValueAsBytes(payload));
      String body = h + "." + p;
      String sig = sign(body);
      return body + "." + sig;
    } catch (Exception ex) {
      throw new IllegalStateException("token_issue_failed", ex);
    }
  }

  private LocalClaims parseTyped(String token, String expectedType) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) throw new IllegalStateException("invalid_token");
      String body = parts[0] + "." + parts[1];
      String actualSig = sign(body);
      if (!constantTimeEquals(actualSig, parts[2])) throw new IllegalStateException("invalid_token");

      byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
      Map<?, ?> claims = objectMapper.readValue(payloadBytes, Map.class);
      String typ = String.valueOf(claims.get("typ"));
      if (!expectedType.equals(typ)) {
        throw new IllegalStateException("invalid_token_type");
      }
      String tokenIssuer = String.valueOf(claims.get("iss"));
      if (!issuer.equals(tokenIssuer)) throw new IllegalStateException("invalid_issuer");
      String subject = String.valueOf(claims.get("sub"));
      String sid = String.valueOf(claims.get("sid"));
      Number expNum = (Number) claims.get("exp");
      long exp = expNum == null ? 0L : expNum.longValue();
      if (exp <= Instant.now().getEpochSecond()) throw new IllegalStateException("token_expired");
      if (subject == null || subject.trim().isEmpty() || sid == null || sid.trim().isEmpty()) {
        throw new IllegalStateException("invalid_token_claims");
      }
      return new LocalClaims(subject, UUID.fromString(sid), exp);
    } catch (IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("invalid_token", ex);
    }
  }

  private String sign(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(jwtSecret, "HmacSHA256"));
      byte[] sig = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      return base64Url(sig);
    } catch (Exception ex) {
      throw new IllegalStateException("sign_failed", ex);
    }
  }

  private String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private boolean constantTimeEquals(String a, String b) {
    try {
      byte[] ab = a.getBytes(StandardCharsets.UTF_8);
      byte[] bb = b.getBytes(StandardCharsets.UTF_8);
      return MessageDigest.isEqual(ab, bb);
    } catch (Exception ex) {
      return false;
    }
  }

  public static class LocalClaims {
    private final String userId;
    private final UUID sessionId;
    private final long expEpochSeconds;

    public LocalClaims(String userId, UUID sessionId, long expEpochSeconds) {
      this.userId = userId;
      this.sessionId = sessionId;
      this.expEpochSeconds = expEpochSeconds;
    }

    public String userId() { return userId; }
    public UUID sessionId() { return sessionId; }
    public long expEpochSeconds() { return expEpochSeconds; }
  }
}
