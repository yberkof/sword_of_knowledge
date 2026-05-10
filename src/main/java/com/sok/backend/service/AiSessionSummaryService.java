package com.sok.backend.service;

import com.sok.backend.config.AiConfigProperties;
import com.sok.backend.persistence.MatchHistoryRepository;
import com.sok.backend.persistence.UserSessionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AiSessionSummaryService {
  private static final Logger log = LoggerFactory.getLogger(AiSessionSummaryService.class);
  private final MatchHistoryRepository historyRepository;
  private final UserSessionRepository sessionRepository;
  private final AiConfigProperties aiConfig;
  private final RestTemplate restTemplate = new RestTemplate();

  public AiSessionSummaryService(
      MatchHistoryRepository historyRepository,
      UserSessionRepository sessionRepository,
      AiConfigProperties aiConfig) {
    this.historyRepository = historyRepository;
    this.sessionRepository = sessionRepository;
    this.aiConfig = aiConfig;
  }

  public String getOrGenerateSummary(String userId) {
    LocalDate today = LocalDate.now();
    Optional<String> existing = sessionRepository.getSummary(userId, today);
    if (existing.isPresent()) {
      return existing.get();
    }

    List<Map<String, Object>> matches = historyRepository.getMatchHistory(userId, 10, 0);
    if (matches.isEmpty()) {
      return "You haven't played any matches today. The battlefield awaits, brave warrior!";
    }

    String summary = generateSummary(userId, matches);
    sessionRepository.saveSummary(userId, today, summary);
    return summary;
  }

  private String generateSummary(String userId, List<Map<String, Object>> matches) {
    if (aiConfig.getApiKey() == null || aiConfig.getApiKey().trim().isEmpty()) {
      return "The Sage is currently meditating. (OpenAI API key missing)";
    }

    try {
      int totalGames = matches.size();
      int wins = 0;
      int totalScore = 0;
      int totalXp = 0;
      for (Map<String, Object> m : matches) {
        int place = ((Number) m.get("place")).intValue();
        if (place == 1) wins++;
        totalScore += ((Number) m.get("score")).intValue();
        totalXp += ((Number) m.get("xp_earned")).intValue();
      }

      String prompt =
          String.format(
              "You are 'The Sage', a wise and slightly witty mentor in a game called 'Sword of Knowledge'. "
                  + "Summarize the player's recent performance based on these stats: "
                  + "Matches played: %d, Wins: %d, Total Score: %d, XP Gained: %d. "
                  + "Keep it short, encouraging, and in character.",
              totalGames, wins, totalScore, totalXp);

      return callOpenAi(prompt);
    } catch (Exception e) {
      log.error("Failed to generate AI summary for user {}", userId, e);
      return "The Sage is resting. Come back later for your wisdom.";
    }
  }

  private String callOpenAi(String prompt) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(aiConfig.getApiKey());

    Map<String, Object> request = new HashMap<>();
    request.put("model", aiConfig.getModel());
    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of("role", "user", "content", prompt));
    request.put("messages", messages);
    request.put("max_tokens", 150);

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
    Map<String, Object> response =
        restTemplate.postForObject("https://api.openai.com/v1/chat/completions", entity, Map.class);

    if (response != null && response.containsKey("choices")) {
      List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
      if (!choices.isEmpty()) {
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
      }
    }
    return "The Sage is silent today.";
  }
}
