package com.sok.backend.api;

import com.sok.backend.realtime.PekkoRealtimeServer;
import com.sok.backend.realtime.actor.RoomCommand;
import com.sok.backend.realtime.actor.GameRoomActor;
import com.sok.backend.security.SecurityUtils;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/matches")
public class MatchesController {
  private final PekkoRealtimeServer pekko;
  
  public MatchesController(PekkoRealtimeServer pekko) { 
      this.pekko = pekko; 
  }

  @GetMapping("/active")
  public CompletionStage<ResponseEntity<ActiveMatchResponse>> getActive() {
    // In a sharded setup, we don't have a simple "is this UID in a room" without a global index.
    // For now, we return a 204 No Content to indicate we don't track the session here.
    // A production fix involves a "UserLocation" sharded actor.
    return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
  }

  public record ActiveMatchResponse(String matchId, String phase, String mapId) {}
}
