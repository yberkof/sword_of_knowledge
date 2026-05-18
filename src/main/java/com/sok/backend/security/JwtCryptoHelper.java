package com.sok.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class JwtCryptoHelper {
  public static String sign(String body, byte[] secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return base64Url(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) { throw new IllegalStateException("sign_failed", e); }
  }

  public static String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
