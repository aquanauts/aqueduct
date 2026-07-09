package com.aquatic.graphql.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Websocket handler for GraphQL subscriptions, supporting both the {@code graphql-transport-ws}
 * and legacy {@code graphql-ws} subprotocols.
 *
 * <p>Public because Jetty accesses listener methods through {@code MethodHandles}, which
 * requires a public class; construction is package-private on purpose.
 */
public class GraphQLWebSocketHandler extends Session.Listener.AbstractAutoDemanding {
    private static final Logger log = LoggerFactory.getLogger(GraphQLWebSocketHandler.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final GraphQL graphQL;
    private final ObjectMapper objectMapper;
    private final Map<String, Subscription> activeSubscriptions = new ConcurrentHashMap<>();
    private boolean connectionInitialized = false;
    private String protocol;

    GraphQLWebSocketHandler(GraphQL graphQL, ObjectMapper objectMapper) {
        this.graphQL = graphQL;
        this.objectMapper = objectMapper;
    }

    int activeSubscriptionCount() {
        return activeSubscriptions.size();
    }

    @Override
    public void onWebSocketOpen(Session session) {
        super.onWebSocketOpen(session);
        this.protocol = session.getUpgradeResponse().getAcceptedSubProtocol();
    }

    @Override
    public void onWebSocketText(String message) {
        try {
            Map<String, Object> request = objectMapper.readValue(message, MAP_TYPE);

            String type = (String) request.get("type");

            if ("graphql-transport-ws".equals(protocol)) {
                handleTransportWsMessage(type, request);
            } else {
                handleLegacyWsMessage(type, request);
            }

        } catch (Exception e) {
            sendError("Error processing message: " + e.getMessage());
        }
    }

    private void handleTransportWsMessage(String type, Map<String, Object> request) {
        switch (type) {
            case "connection_init":
                handleConnectionInit();
                break;
            case "subscribe":
                if (!connectionInitialized) {
                    sendError("Connection not initialized");
                    return;
                }
                handleSubscribe(request);
                break;
            case "complete":
                handleComplete(request);
                break;
            case "ping":
                sendMessage(Map.of("type", "pong"));
                break;
            case "pong":
                // Client pong response, no action needed
                break;
            default:
                sendError("Unknown graphql-transport-ws message type: " + type);
        }
    }

    private void handleLegacyWsMessage(String type, Map<String, Object> request) {
        switch (type) {
            case "connection_init":
                handleConnectionInit();
                break;
            case "start":
                if (!connectionInitialized) {
                    sendError("Connection not initialized");
                    return;
                }
                handleSubscriptionStart(request);
                break;
            case "stop":
                handleSubscriptionStop(request);
                break;
            case "connection_terminate":
                getSession().close();
                break;
            default:
                sendError("Unknown graphql-ws message type: " + type);
        }
    }

    private void handleConnectionInit() {
        connectionInitialized = true;

        if ("graphql-transport-ws".equals(protocol)) {
            sendMessage(Map.of("type", "connection_ack"));
        } else {
            sendMessage(Map.of("type", "connection_ack"));
            sendMessage(Map.of("type", "ka")); // Keep alive for legacy protocol
        }
    }

    private void handleSubscribe(Map<String, Object> request) {
        String id = (String) request.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) request.get("payload");

        executeSubscription(id, payload);
    }

    private void handleSubscriptionStart(Map<String, Object> request) {
        String id = (String) request.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) request.get("payload");

        executeSubscription(id, payload);
    }

    private void executeSubscription(String id, Map<String, Object> payload) {
        if (payload == null) {
            sendError("Missing payload in subscription message");
            return;
        }

        String query = payload.get("query").toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) payload.get("variables");

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .variables(variables != null ? variables : Map.of())
                .build();

        ExecutionResult result = graphQL.execute(executionInput);

        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            sendSubscriptionError(id, result.getErrors());
            return;
        }

        if (result.getData() instanceof Publisher) {
            Publisher<Object> publisher = result.getData();

            publisher.subscribe(new Subscriber<>() {
                @Override
                public void onSubscribe(Subscription subscription) {
                    activeSubscriptions.put(id, subscription);
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(Object data) {
                    try {
                        // Handle the data properly - it should be an ExecutionResult
                        if (data instanceof ExecutionResult execResult) {
                            sendSubscriptionResult(id, execResult);
                        } else {
                            // If it's raw data, wrap it in a proper ExecutionResult format
                            sendSubscriptionData(id, data);
                        }
                    } catch (Exception e) {
                        sendSubscriptionError(
                                id,
                                Collections.singletonList(
                                        Map.of("message", "Error processing subscription data: " + e.getMessage())));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    sendSubscriptionError(
                            id,
                            Collections.singletonList(Map.of(
                                    "message",
                                    t.getMessage(),
                                    "locations",
                                    Collections.emptyList(),
                                    "path",
                                    Collections.emptyList())));
                    activeSubscriptions.remove(id);
                }

                @Override
                public void onComplete() {
                    sendSubscriptionComplete(id);
                    activeSubscriptions.remove(id);
                }
            });
        } else {
            sendSubscriptionError(id, Collections.singletonList(Map.of("message", "Not a subscription query")));
        }
    }

    private void handleComplete(Map<String, Object> request) {
        String id = (String) request.get("id");
        handleSubscriptionStop(Map.of("id", id));
    }

    private void handleSubscriptionStop(Map<String, Object> request) {
        String id = (String) request.get("id");
        Subscription subscription = activeSubscriptions.remove(id);
        if (subscription != null) {
            subscription.cancel();
        }
    }

    private void sendSubscriptionResult(String id, ExecutionResult result) {
        Map<String, Object> payload = Map.of(
                "data", result.getData() != null ? result.getData() : Collections.emptyMap(),
                "errors", result.getErrors() != null ? result.getErrors() : Collections.emptyList());

        handleProtocols(id, payload);
    }

    private void sendSubscriptionData(String id, Object data) {
        Map<String, Object> payload =
                Map.of("data", data != null ? data : Collections.emptyMap(), "errors", Collections.emptyList());

        handleProtocols(id, payload);
    }

    private void handleProtocols(String id, Map<String, Object> payload) {
        Map<String, Object> message;
        if ("graphql-transport-ws".equals(protocol)) {
            message = Map.of("id", id, "type", "next", "payload", payload);
        } else {
            message = Map.of("id", id, "type", "data", "payload", payload);
        }
        sendMessage(message);
    }

    private void sendSubscriptionError(String id, Object errors) {
        Map<String, Object> payload;
        if (errors instanceof String) {
            payload = Map.of(
                    "data", Collections.emptyMap(),
                    "errors", Collections.singletonList(Map.of("message", errors)));
        } else {
            payload = Map.of("data", Collections.emptyMap(), "errors", errors);
        }

        Map<String, Object> message = Map.of("id", id, "type", "error", "payload", payload);
        sendMessage(message);
    }

    private void sendSubscriptionComplete(String id) {
        sendMessage(Map.of("id", id, "type", "complete"));
    }

    private void sendError(String errorMessage) {
        Map<String, Object> error = Map.of("type", "error", "payload", Map.of("message", errorMessage));
        sendMessage(error);
    }

    private void sendMessage(Map<String, Object> message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize websocket message", e);
            return;
        }
        Session session = getSession();
        if (session != null && session.isOpen()) {
            session.sendText(
                    json, Callback.from(() -> {}, failure -> log.info("Could not send websocket message", failure)));
        }
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        log.info("Websocket error", cause);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        activeSubscriptions.values().forEach(subscription -> {
            try {
                subscription.cancel();
            } catch (Exception e) {
                log.info("Could not cancel websocket subscription", e);
            }
        });
        activeSubscriptions.clear();
    }
}
