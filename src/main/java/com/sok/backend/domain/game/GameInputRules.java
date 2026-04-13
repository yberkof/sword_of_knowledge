package com.sok.backend.domain.game;

import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GameInputRules {
  private static final Pattern INVITE_NON_ALNUM = Pattern.compile("[^a-zA-Z0-9]");
  private static final Set<String> PROFANITY =
      new HashSet<String>(Arrays.asList("spam", "curse"));

  public String normalizePrivateCode(String raw) {
    String input = raw == null ? "" : raw;
    String normalized = INVITE_NON_ALNUM.matcher(input).replaceAll("").toUpperCase(Locale.ROOT);
    return normalized.length() > 8 ? normalized.substring(0, 8) : normalized;
  }

  public String sanitizeChatMessage(String text) {
    String clipped = text == null ? "" : text.substring(0, Math.min(280, text.length()));
    String lowered = clipped.toLowerCase(Locale.ROOT);
    for (String bad : PROFANITY) {
      if (lowered.contains(bad)) {
        return "";
      }
    }
    return clipped.trim();
  }

  public Integer coerceChoiceIndex(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      Number number = (Number) value;
      return (int) Math.floor(number.doubleValue());
    }
    if (value instanceof String) {
      String s = (String) value;
      if (s.trim().isEmpty()) {
        return null;
      }
      try {
        return Integer.parseInt(s.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
