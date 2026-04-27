package com.sok.backend.realtime;

import com.sok.backend.realtime.match.PlayerState;
import com.sok.backend.realtime.match.RoomState;
import com.sok.backend.realtime.room.RoomStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Registers Micrometer gauges for the realtime room store (moved from {@link SocketGateway}).
 */
@Component
public class RealtimePlayerGaugeRegistry {

  private final RoomStore store;
  private final MeterRegistry meterRegistry;

  public RealtimePlayerGaugeRegistry(RoomStore store, MeterRegistry meterRegistry) {
    this.store = store;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  public void registerGauges() {
    Gauge.builder("sok.realtime.rooms", store.rooms(), Map::size).register(meterRegistry);
    Gauge.builder("sok.realtime.players_online", this, o -> o.currentOnlineCount()).register(meterRegistry);
  }

  private double currentOnlineCount() {
    int c = 0;
    for (RoomState r : store.values()) {
      for (PlayerState p : r.players) {
        if (p.online && !p.isEliminated) c++;
      }
    }
    return c;
  }
}
