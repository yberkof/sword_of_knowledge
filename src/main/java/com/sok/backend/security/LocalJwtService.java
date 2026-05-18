package com.sok.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalJwtService {
  private final byte[] secret;
  private final String issuer;
  private final long accessTtl;
  private final long refreshTtl;
  private final ObjectMapper mapper = new ObjectMapper();

  public LocalJwtService(@Value("${app.auth.jwt-secret:dev_change_me_local_jwt_secret_32_chars_min}") String secret,
      @Value("${app.auth.issuer:sok-local-auth}") String issuer,
      @Value("${app.auth.access-ttl-seconds:900}") long accessTtl,
      @Value("${app.auth.refresh-ttl-seconds:2592000}") long refreshTtl) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.issuer = issuer; this.accessTtl = accessTtl; this.refreshTtl = refreshTtl;
  }

  public String issueAccessToken(String u, UUID s) { return issue(u, s, "access", accessTtl); }
  public String issueRefreshToken(String u, UUID s) { return issue(u, s, "refresh", refreshTtl); }
  public LocalClaims verifyAccessToken(String t) { return parse(t, "access"); }
  public LocalClaims verifyRefreshToken(String t) { return parse(t, "refresh"); }

  private String issue(String u, UUID s, String t, long ttl) {
    long now = Instant.now().getEpochSecond();
    var p = Map.of("sub", u, "iss", issuer, "iat", now, "exp", now + ttl, "typ", t, "sid", s.toString());
    try {
      String h = JwtCryptoHelper.base64Url(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
      String b = h + "." + JwtCryptoHelper.base64Url(mapper.writeValueAsBytes(p));
      return b + "." + JwtCryptoHelper.sign(b, secret);
    } catch (Exception e) { throw new IllegalStateException("issue_fail", e); }
  }

  private LocalClaims parse(String t, String typ) {
    try {
      var parts = t.split("\\."); if (parts.length != 3) throw new IllegalStateException("invalid_token");
      if (!JwtCryptoHelper.constantTimeEquals(JwtCryptoHelper.sign(parts[0] + "." + parts[1], secret), parts[2])) throw new IllegalStateException("invalid_sig");
      var c = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
      if (!typ.equals(c.get("typ")) || !issuer.equals(c.get("iss"))) throw new IllegalStateException("invalid_type_or_iss");
      long exp = ((Number) c.get("exp")).longValue();
      if (exp <= Instant.now().getEpochSecond()) throw new IllegalStateException("expired");
      return new LocalClaims((String) c.get("sub"), UUID.fromString((String) c.get("sid")), exp);
    } catch (Exception e) { throw new IllegalStateException("verify_fail", e); }
  }
}
