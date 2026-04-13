package com.sok.backend.auth.identity;

import java.util.Locale;
import java.util.regex.Pattern;

public final class IdentityEmail {
  private static final Pattern SIMPLE =
      Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", Pattern.CASE_INSENSITIVE);

  private IdentityEmail() {}

  public static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String t = raw.trim().toLowerCase(Locale.ROOT);
    return t.isEmpty() ? null : t;
  }

  public static boolean isValid(String normalized) {
    return normalized != null
        && normalized.length() <= 254
        && SIMPLE.matcher(normalized).matches();
  }
}
