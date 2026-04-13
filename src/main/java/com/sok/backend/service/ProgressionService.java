package com.sok.backend.service;

import com.sok.backend.persistence.EconomyRepository;
import com.sok.backend.persistence.UserRecord;
import com.sok.backend.persistence.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressionService {
  private final EconomyRepository economyRepository;
  private final UserRepository userRepository;

  public ProgressionService(EconomyRepository economyRepository, UserRepository userRepository) {
    this.economyRepository = economyRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public boolean grantMatchResult(String uid, int place, String matchId) {
    int xp = place == 1 ? 120 : (place == 2 ? 70 : 40);
    int trophies = place == 1 ? 20 : (place == 2 ? 8 : -4);
    String key = "match:" + matchId + ":" + uid + ":place:" + place;
    boolean ok =
        economyRepository.applyTransaction(
            uid,
            key,
            "progression",
            "match_result",
            0,
            0,
            xp,
            trophies,
            matchId,
            "{\"place\":" + place + "}");
    if (!ok) return false;
    Optional<UserRecord> row = userRepository.findById(uid);
    if (!row.isPresent()) return true;
    int newLevel = levelFromXp(row.get().xp());
    userRepository.updateLevelIfHigher(uid, newLevel);
    return true;
  }

  int levelFromXp(int xp) {
    if (xp <= 0) return 1;
    int level = 1;
    int req = 100;
    int current = xp;
    while (current >= req && level < 200) {
      current -= req;
      level++;
      req += 25;
    }
    return level;
  }
}
