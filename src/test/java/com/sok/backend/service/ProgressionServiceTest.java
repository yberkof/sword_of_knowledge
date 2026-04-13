package com.sok.backend.service;

import com.sok.backend.persistence.EconomyRepository;
import com.sok.backend.persistence.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProgressionServiceTest {
  @Test
  void levelFormulaMonotonic() {
    EconomyRepository economyRepository = Mockito.mock(EconomyRepository.class);
    UserRepository userRepository = Mockito.mock(UserRepository.class);
    ProgressionService service = new ProgressionService(economyRepository, userRepository);
    int l1 = service.levelFromXp(0);
    int l2 = service.levelFromXp(120);
    int l3 = service.levelFromXp(1000);
    Assertions.assertTrue(l1 <= l2);
    Assertions.assertTrue(l2 <= l3);
    Assertions.assertEquals(1, l1);
  }
}
