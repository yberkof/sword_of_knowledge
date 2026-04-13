package com.sok.backend.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RefreshTokenHasherTest {
  @Test
  void hashingIsDeterministic() {
    RefreshTokenHasher hasher = new RefreshTokenHasher();
    String h1 = hasher.sha256("abc");
    String h2 = hasher.sha256("abc");
    Assertions.assertEquals(h1, h2);
    Assertions.assertEquals(64, h1.length());
  }
}
