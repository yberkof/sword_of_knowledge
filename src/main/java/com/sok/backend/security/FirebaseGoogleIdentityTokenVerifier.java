package com.sok.backend.security;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
@Component
public class FirebaseGoogleIdentityTokenVerifier {
  private static final String CERT_URL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
  private final String projectId;
  private final RestTemplate http = new RestTemplate(new SimpleClientHttpRequestFactory());
  private final ObjectMapper mapper = new ObjectMapper();
  private volatile Map<String, String> certs;
  private volatile long loadedAt;
  public FirebaseGoogleIdentityTokenVerifier(@Value("${app.firebase.project-id:}") String projectId) {
    this.projectId = projectId == null ? "" : projectId.trim();
  }
  public boolean isConfigured() { return !projectId.isEmpty(); }
  public VerifiedToken verify(String idToken) {
    if (!isConfigured() || idToken == null || idToken.isBlank()) throw new IllegalArgumentException("invalid_token");
    String[] parts = idToken.trim().split("\\.");
    if (parts.length != 3) throw new IllegalArgumentException("invalid_token");
    try {
      JsonNode header = mapper.readTree(decode(parts[0]));
      String kid = header.path("kid").asText("");
      if (kid.isEmpty()) throw new IllegalArgumentException("invalid_token");
      String pem = getCerts().get(kid);
      if (pem == null) { refresh(); pem = getCerts().get(kid); }
      if (pem == null) throw new IllegalArgumentException("invalid_token");
      X509Certificate cert = parsePem(pem);
      Signature sig = Signature.getInstance("SHA256withRSA");
      sig.initVerify(cert.getPublicKey());
      sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
      if (!sig.verify(decode(parts[2]))) throw new IllegalArgumentException("invalid_token");
      JsonNode payload = mapper.readTree(decode(parts[1]));
      if (payload.path("exp").asLong(0) * 1000L < System.currentTimeMillis()) throw new IllegalArgumentException("invalid_token");
      if (!("https://securetoken.google.com/" + projectId).equals(payload.path("iss").asText())) throw new IllegalArgumentException("invalid_token");
      if (!audMatches(payload.path("aud"))) throw new IllegalArgumentException("invalid_token");
      String uid = payload.path("sub").asText("");
      if (uid.isEmpty()) throw new IllegalArgumentException("invalid_token");
      String name = payload.path("name").asText("Warrior").trim();
      return new VerifiedToken(uid, payload.path("email").asText(""), name.isEmpty() ? "Warrior" : name);
    } catch (Exception e) { throw new IllegalArgumentException("invalid_token", e); }
  }
  private boolean audMatches(JsonNode aud) {
    if (aud.isTextual()) return projectId.equals(aud.asText());
    if (aud.isArray()) for (JsonNode n : aud) if (projectId.equals(n.asText())) return true;
    return false;
  }
  private byte[] decode(String s) { return Base64.getUrlDecoder().decode(s); }
  private Map<String, String> getCerts() {
    if (certs == null) certs = new ConcurrentHashMap<>();
    return certs;
  }
  private synchronized void refresh() {
    if (certs != null && !certs.isEmpty() && System.currentTimeMillis() - loadedAt < 3600000L) return;
    try {
      String body = http.getForObject(CERT_URL, String.class);
      certs = new ConcurrentHashMap<>(mapper.readValue(body, new TypeReference<Map<String, String>>() {}));
      loadedAt = System.currentTimeMillis();
    } catch (Exception e) { throw new IllegalStateException("cert_fetch_failed", e); }
  }
  private X509Certificate parsePem(String pem) throws Exception {
    byte[] der = Base64.getDecoder().decode(pem.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----", "").replaceAll("\\s+", ""));
    return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
  }
}
