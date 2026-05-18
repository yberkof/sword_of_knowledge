package com.sok.backend.api;

import javax.servlet.http.HttpServletRequest;

public final class HttpUtils {
  private HttpUtils() {}

  public static String extractIp(HttpServletRequest request) {
    String header = request.getHeader("X-Forwarded-For");
    if (header != null && !header.trim().isEmpty()) {
      int comma = header.indexOf(',');
      if (comma > 0) return header.substring(0, comma).trim();
      return header.trim();
    }
    return request.getRemoteAddr();
  }
}
