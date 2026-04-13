package com.sok.backend.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Verifies Firebase Auth ID tokens (Google sign-in on the client) using Google's published
 * x509 certs. JDK-only signature check (no nimbus / Firebase Admin).
 */
@Component
public class FirebaseGoogleIdentityTokenVerifier {
  private static final String CERT_URL =
      "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
  private static final long CERT_CACHE_MS = 3600_000L;

  private final String projectId;
  private final RestTemplate http = new RestTemplate(new SimpleClientHttpRequestFactory());
  private final ObjectMapper objectMapper = new ObjectMapper();

  private volatile Map<String, String> certPemByKid;
  private volatile long certLoadedAt;

  public FirebaseGoogleIdentityTokenVerifier(
      @Value("${app.firebase.project-id:}") String projectId) {
    this.projectId = projectId == null ? "" : projectId.trim();
  }

  public boolean isConfigured() {
    return !projectId.isEmpty();
  }

  public VerifiedToken verify(String idToken) {
    if (!isConfigured()) {
      throw new IllegalStateException("identity_not_configured");
    }
    if (idToken == null || idToken.trim().isEmpty()) {
      throw new IllegalArgumentException("invalid_token");
    }
    String compact = idToken.trim();
    String[] parts = compact.split("\\.");
    if (parts.length != 3) {
      throw new IllegalArgumentException("invalid_token");
    }
    try {
      byte[] headerJson = b64UrlDecode(parts[0]);
      JsonNode header = objectMapper.readTree(headerJson);
      String kid = header.has("kid") && !header.get("kid").isNull() ? header.get("kid").asText() : null;
      if (kid == null || kid.isEmpty()) {
        throw new IllegalArgumentException("invalid_token");
      }
      String pem = loadCerts().get(kid);
      if (pem == null) {
        synchronized (this) {
          certPemByKid = null;
        }
        refreshCerts();
        pem = loadCerts().get(kid);
      }
      if (pem == null) {
        throw new IllegalArgumentException("invalid_token");
      }
      X509Certificate cert = parsePem(pem);
      byte[] signature = b64UrlDecode(parts[2]);
      byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
      Signature sig = Signature.getInstance("SHA256withRSA");
      sig.initVerify(cert.getPublicKey());
      sig.update(signingInput);
      if (!sig.verify(signature)) {
        throw new IllegalArgumentException("invalid_token");
      }

      byte[] payloadJson = b64UrlDecode(parts[1]);
      JsonNode payload = objectMapper.readTree(payloadJson);
      long expSec = payload.has("exp") && !payload.get("exp").isNull() ? payload.get("exp").asLong() : 0L;
      if (expSec <= 0 || expSec * 1000L < System.currentTimeMillis()) {
        throw new IllegalArgumentException("invalid_token");
      }
      String iss = payload.has("iss") && !payload.get("iss").isNull() ? payload.get("iss").asText() : null;
      String expectedIss = "https://securetoken.google.com/" + projectId;
      if (iss == null || !expectedIss.equals(iss)) {
        throw new IllegalArgumentException("invalid_token");
      }
      if (!audienceMatches(payload.get("aud"))) {
        throw new IllegalArgumentException("invalid_token");
      }
      String uid =
          payload.has("sub") && !payload.get("sub").isNull() ? payload.get("sub").asText() : null;
      if (uid == null || uid.isEmpty()) {
        throw new IllegalArgumentException("invalid_token");
      }
      String email = payload.has("email") && !payload.get("email").isNull() ? payload.get("email").asText() : "";
      String name =
          payload.has("name") && !payload.get("name").isNull() ? payload.get("name").asText() : "Warrior";
      if (name.trim().isEmpty()) {
        name = "Warrior";
      }
      return new VerifiedToken(uid, email, name);
    } catch (IOException e) {
      throw new IllegalArgumentException("invalid_token", e);
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("invalid_token", e);
    }
  }

  private boolean audienceMatches(JsonNode aud) {
    if (aud == null || aud.isNull()) {
      return false;
    }
    if (aud.isTextual()) {
      return projectId.equals(aud.asText());
    }
    if (aud.isArray()) {
      for (JsonNode n : aud) {
        if (projectId.equals(n.asText())) {
          return true;
        }
      }
    }
    return false;
  }

  private static byte[] b64UrlDecode(String segment) {
    return Base64.getUrlDecoder().decode(segment);
  }

  private Map<String, String> loadCerts() {
    Map<String, String> m = certPemByKid;
    if (m == null) {
      m = new ConcurrentHashMap<>();
      certPemByKid = m;
    }
    return m;
  }

  private void refreshCerts() {
    long now = System.currentTimeMillis();
    if (certPemByKid != null && !certPemByKid.isEmpty() && now - certLoadedAt < CERT_CACHE_MS) {
      return;
    }
    synchronized (this) {
      if (certPemByKid != null && !certPemByKid.isEmpty() && now - certLoadedAt < CERT_CACHE_MS) {
        return;
      }
      String body = http.getForObject(CERT_URL, String.class);
      if (body == null || body.isEmpty()) {
        throw new IllegalStateException("cert_fetch_failed");
      }
      try {
        Map<String, String> parsed =
            objectMapper.readValue(body, new TypeReference<Map<String, String>>() {});
        certPemByKid = new ConcurrentHashMap<>(parsed);
        certLoadedAt = System.currentTimeMillis();
      } catch (IOException e) {
        throw new IllegalStateException("cert_fetch_failed", e);
      }
    }
  }

  private static X509Certificate parsePem(String pem) {
    try {
      String b64 =
          pem.replace("-----BEGIN CERTIFICATE-----", "")
              .replace("-----END CERTIFICATE-----", "")
              .replaceAll("\\s+", "");
      byte[] der = Base64.getDecoder().decode(b64);
      return (X509Certificate)
          CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    } catch (CertificateException | IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid_token", e);
    }
  }

  public static final class VerifiedToken {
    private final String uid;
    private final String email;
    private final String name;

    public VerifiedToken(String uid, String email, String name) {
      this.uid = uid;
      this.email = email;
      this.name = name;
    }

    public String uid() {
      return uid;
    }

    public String email() {
      return email;
    }

    public String name() {
      return name;
    }
  }
}
