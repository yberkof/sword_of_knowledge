package com.sok.backend.config;

import java.net.InetAddress;
import java.net.URI;

/** Loopback + RFC1918-style LAN hosts for permissive local dev (CORS + Socket.IO). */
public final class DevOriginUtil {

  private DevOriginUtil() {}

  public static boolean isLocalPrivateDevOrigin(String origin) {
    if (origin == null || origin.isEmpty()) {
      return false;
    }
    try {
      URI u = URI.create(origin);
      String host = u.getHost();
      if (host == null || host.isEmpty()) {
        return false;
      }
      InetAddress addr = InetAddress.getByName(host);
      return addr.isLoopbackAddress() || addr.isSiteLocalAddress();
    } catch (Exception e) {
      return false;
    }
  }
}
