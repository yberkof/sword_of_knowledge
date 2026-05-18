package com.sok.backend.realtime;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.typed.javadsl.ActorSource;
import org.apache.pekko.stream.typed.javadsl.ActorSink;
import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.AsPublisher;

import com.sok.backend.realtime.actor.RoomCommand;
import com.sok.backend.realtime.actor.UserSessionActor;
import com.sok.backend.realtime.actor.GameRoomActor;
import com.sok.backend.service.config.RuntimeGameConfigService;
import com.sok.backend.domain.game.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Optional;
import java.util.List;

@Component
public class PekkoRealtimeServer extends AllDirectives {

    private final ActorSystem<Void> system;
    private final ClusterSharding sharding;
    
    @Value("${app.socket.port:8081}")
    private int port;

    public PekkoRealtimeServer(
            QuestionEngineService questionEngine,
            RuntimeGameConfigService config,
            ClaimingPhaseService claimingService,
            RoomSnapshotFactory snapshotFactory) {
        
        // Guardians are the standard Pekko Typed entry point
        this.system = ActorSystem.create(Behaviors.empty(), "SokRealtimeSystem");
        this.sharding = ClusterSharding.get(system);

        // Sharding initialization with factory function
        this.sharding.init(
            Entity.of(GameRoomActor.ENTITY_TYPE_KEY, ctx -> 
                GameRoomActor.create(ctx.getEntityId(), questionEngine, config, claimingService, snapshotFactory)
            )
        );
    }

    @PostConstruct
    public void start() {
        Http.get(system).newServerAt("0.0.0.0", port).bind(createRoute());
        system.log().info("Pekko Realtime Edge online on port {}", port);
    }

    private Route createRoute() {
        List<HttpHeader> corsHeaders = List.of(
            HttpHeader.parse("Access-Control-Allow-Origin", "*"),
            HttpHeader.parse("Access-Control-Allow-Methods", "GET, POST, OPTIONS"),
            HttpHeader.parse("Access-Control-Allow-Headers", "*")
        );

        return respondWithDefaultHeaders(corsHeaders, () -> 
            concat(
                path("health", () -> complete("OK")),
                pathPrefix("api", () -> 
                    path("ws", () -> 
                        parameter("uid", uid -> 
                            parameter("roomId", roomId ->
                                handleWebSocketMessages(createWebSocketFlow(uid, roomId))
                            )
                        )
                    )
                ),
                options(() -> complete(HttpResponse.create().withStatus(200)))
            )
        );
    }

    /**
     * High-quality backpressured WebSocket flow.
     */
    private Flow<Message, Message, NotUsed> createWebSocketFlow(String uid, String roomId) {
        // 1. WebSocket Sink (Outgoing from Actor -> Client)
        Source<Message, ActorRef<Message>> source = ActorSource.actorRef(
                m -> false, 
                m -> Optional.empty(),
                1024, // Production buffer
                OverflowStrategy.dropTail()
        );

        return Flow.fromSinkAndSourceCoupledMat(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), source, (pub, sinkRef) -> {
            // 2. UserSessionActor manages the bridge
            ActorRef<UserSessionActor.Command> sessionActor = system.systemActorOf(
                    UserSessionActor.create(uid, roomId, sharding, sinkRef),
                    "session-" + uid + "-" + System.nanoTime(),
                    org.apache.pekko.actor.typed.Props.empty()
            );

            // 3. Client -> WebSocket -> UserSessionActor
            Sink<Message, NotUsed> sink = Flow.<Message>create()
                    .map(m -> (UserSessionActor.Command) new UserSessionActor.InboundMessage(m.asTextMessage().getStrictText()))
                    .to(ActorSink.actorRef(
                            sessionActor,
                            new UserSessionActor.ConnectionClosed(),
                            UserSessionActor.ConnectionFailed::new
                    ));

            Source.fromPublisher(pub).runWith(sink, system);

            return NotUsed.getInstance();
        });
    }

    @PreDestroy
    public void stop() {
        system.terminate();
    }
}
