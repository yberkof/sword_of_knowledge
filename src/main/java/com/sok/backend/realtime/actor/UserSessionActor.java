package com.sok.backend.realtime.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges one WebSocket connection to a sharded {@link GameRoomActor}.
 */
public class UserSessionActor extends AbstractBehavior<UserSessionActor.Command> {

    private static final Logger log = LoggerFactory.getLogger(UserSessionActor.class);

    public interface Command {}
    public record InboundMessage(String text) implements Command {}
    public record DeliverToClient(RoomCommand.RoomEvent event) implements Command {}
    public record ConnectionClosed() implements Command {}
    public record ConnectionFailed(Throwable cause) implements Command {}

    private final String uid;
    private final ActorRef<Message> websocketSink;
    private final EntityRef<RoomCommand> roomRef;
    private final ActorRef<RoomCommand.RoomEvent> outbound;
    private final ObjectMapper mapper = new ObjectMapper();

    public static Behavior<Command> create(
            String uid, String roomId, ClusterSharding sharding, ActorRef<Message> websocketSink) {
        return Behaviors.setup(ctx -> new UserSessionActor(ctx, uid, roomId, sharding, websocketSink));
    }

    private UserSessionActor(
            ActorContext<Command> context,
            String uid,
            String roomId,
            ClusterSharding sharding,
            ActorRef<Message> websocketSink) {
        super(context);
        this.uid = uid;
        this.websocketSink = websocketSink;
        this.roomRef = sharding.entityRefFor(GameRoomActor.ENTITY_TYPE_KEY, roomId);
        this.outbound = context.messageAdapter(RoomCommand.RoomEvent.class, DeliverToClient::new);
        context.getLog().info("Session for {} attached to room {}", uid, roomId);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(InboundMessage.class, this::onInboundMessage)
                .onMessage(DeliverToClient.class, this::onDeliverToClient)
                .onMessage(ConnectionClosed.class, m -> onDisconnect())
                .onMessage(ConnectionFailed.class, m -> onDisconnect())
                .build();
    }

    private Behavior<Command> onInboundMessage(InboundMessage m) {
        try {
            var node = mapper.readTree(m.text());
            WsCommandParser.parse(uid, node).ifPresent(cmd -> {
                if (cmd instanceof RoomCommand.Join join) {
                    roomRef.tell(new RoomCommand.Join(join.uid(), join.name(), join.avatar(), outbound));
                } else {
                    roomRef.tell(cmd);
                }
            });
        } catch (Exception e) {
            log.error("WS JSON parse error", e);
        }
        return this;
    }

    private Behavior<Command> onDeliverToClient(DeliverToClient m) {
        try {
            String json = mapper.writeValueAsString(m.event());
            websocketSink.tell(TextMessage.create(json));
        } catch (Exception e) {
            log.error("Event serialization error", e);
        }
        return this;
    }

    private Behavior<Command> onDisconnect() {
        roomRef.tell(new RoomCommand.SessionTerminated(uid));
        return Behaviors.stopped();
    }
}
