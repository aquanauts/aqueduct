package com.aquatic.graphql.server;

import com.aquatic.graphql.json.GraphQLJson;
import com.aquatic.graphql.schema.GraphQLProvider;
import com.aquatic.graphql.subscriptions.StateChangePublisher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.annotations.GraphQLSubscription;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end websocket subscription tests over both supported subprotocols. */
class GraphQLWebSocketIntegrationTest {
    private static final ObjectMapper mapper = GraphQLJson.defaultMapper();

    public static class GreetingService {
        final StateChangePublisher<String> greetings = new StateChangePublisher<>();

        @GraphQLQuery
        public String ping() {
            return "pong";
        }

        @GraphQLSubscription(name = "greetings")
        public Publisher<String> greetingUpdates() {
            return greetings;
        }
    }

    /** Collects incoming text frames for assertion. Public so Jetty's MethodHandles lookup can access it. */
    public static class RecordingSocket extends Session.Listener.AbstractAutoDemanding {
        final BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();

        @Override
        public void onWebSocketText(String message) {
            try {
                messages.add(mapper.readValue(message, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        Map<String, Object> nextMessage() throws InterruptedException {
            Map<String, Object> message = messages.poll(5, TimeUnit.SECONDS);
            assertNotNull(message, "Timed out waiting for a websocket message");
            return message;
        }
    }

    private GreetingService service;
    private GraphQLServer server;
    private WebSocketClient client;
    private Session session;
    private RecordingSocket socket;

    @BeforeEach
    void setUp() throws Exception {
        service = new GreetingService();
        server =
                GraphQLServer.builder(GraphQLProvider.from("", service)).port(0).build();
        server.start();
        client = new WebSocketClient();
        client.start();
        socket = new RecordingSocket();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (session != null) {
            session.close();
        }
        client.stop();
        server.stop();
    }

    private void connect(String subProtocol) throws Exception {
        var request = new ClientUpgradeRequest();
        request.setSubProtocols(subProtocol);
        URI wsUri = URI.create("ws://localhost:" + server.port() + "/graphql");
        session = client.connect(socket, wsUri, request).get(5, TimeUnit.SECONDS);
        assertEquals(subProtocol, session.getUpgradeResponse().getAcceptedSubProtocol());
    }

    private void send(String type, String id, Map<String, Object> payload) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        if (id != null) {
            message.put("id", id);
        }
        if (payload != null) {
            message.put("payload", payload);
        }
        session.sendText(mapper.writeValueAsString(message), Callback.NOOP);
    }

    private Map<String, Object> subscriptionPayload() {
        return Map.of("query", "subscription { greetings }");
    }

    @Test
    void supportsGraphqlTransportWsProtocol() throws Exception {
        connect("graphql-transport-ws");

        send("connection_init", null, null);
        assertEquals("connection_ack", socket.nextMessage().get("type"));

        send("subscribe", "sub-1", subscriptionPayload());
        service.greetings.update("hello");

        var next = socket.nextMessage();
        assertEquals("next", next.get("type"));
        assertEquals("sub-1", next.get("id"));
        assertEquals(Map.of("greetings", "hello"), ((Map<?, ?>) next.get("payload")).get("data"));

        send("complete", "sub-1", null);
    }

    @Test
    void answersPingWithPong() throws Exception {
        connect("graphql-transport-ws");
        send("connection_init", null, null);
        assertEquals("connection_ack", socket.nextMessage().get("type"));

        send("ping", null, null);
        assertEquals("pong", socket.nextMessage().get("type"));
    }

    @Test
    void rejectsSubscribeBeforeConnectionInit() throws Exception {
        connect("graphql-transport-ws");
        send("subscribe", "sub-1", subscriptionPayload());

        var error = socket.nextMessage();
        assertEquals("error", error.get("type"));
        assertEquals(Map.of("message", "Connection not initialized"), error.get("payload"));
    }

    @Test
    void supportsLegacyGraphqlWsProtocol() throws Exception {
        connect("graphql-ws");

        send("connection_init", null, null);
        assertEquals("connection_ack", socket.nextMessage().get("type"));
        assertEquals("ka", socket.nextMessage().get("type"));

        send("start", "sub-1", subscriptionPayload());
        service.greetings.update("hi there");

        var data = socket.nextMessage();
        assertEquals("data", data.get("type"));
        assertEquals("sub-1", data.get("id"));
        assertEquals(Map.of("greetings", "hi there"), ((Map<?, ?>) data.get("payload")).get("data"));

        send("stop", "sub-1", null);
    }
}
