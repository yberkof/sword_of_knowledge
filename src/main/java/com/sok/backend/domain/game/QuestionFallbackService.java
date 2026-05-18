package com.sok.backend.domain.game;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class QuestionFallbackService {
  public NumericQuestion nextNumeric() {
    NumericQuestion q = new NumericQuestion();
    int r = ThreadLocalRandom.current().nextInt(3);
    if (r == 0) { q.id = "n-1453"; q.text = "Year Constantinople fell?"; q.answer = 1453; }
    else if (r == 1) { q.id = "n-1440"; q.text = "Minutes in one day?"; q.answer = 1440; }
    else { q.id = "n-366"; q.text = "Days in leap year?"; q.answer = 366; }
    return q;
  }

  public McqQuestion nextMcq() {
    McqQuestion q = new McqQuestion();
    int r = ThreadLocalRandom.current().nextInt(3);
    if (r == 0) { q.id = "m-eg"; q.text = "Capital of Egypt?"; q.options = Arrays.asList("Cairo", "Alex", "Luxor", "Aswan"); q.correctIndex = 0; }
    else if (r == 1) { q.id = "m-pc"; q.text = "Largest ocean?"; q.options = Arrays.asList("Atl", "Ind", "Pac", "Arc"); q.correctIndex = 2; }
    else { q.id = "m-mt"; q.text = "2 + 2?"; q.options = Arrays.asList("1", "2", "3", "4"); q.correctIndex = 3; }
    q.category = "general";
    return q;
  }
}
