package com.sok.backend.domain.game.tiebreaker;

import com.sok.backend.domain.game.QuestionEngineService;
import com.sok.backend.service.config.GameRuntimeConfig;
import java.util.Map;

/**
 * Ports for timers and outbound events — implemented by {@code SocketGateway} so tie-break modules stay
 * IO-free and unit-testable.
 */
public interface TieBreakerRealtimeBridge {

  String roomId();

  void emitToRoom(String eventName, Map<String, Object> payload);

  void scheduleRoomTimer(String timerKey, long delayMs, Runnable task);

  void cancelRoomTimer(String timerKey);

  GameRuntimeConfig configuration();

  QuestionEngineService questionEngine();
}
