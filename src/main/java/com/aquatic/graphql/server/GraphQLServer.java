package com.aquatic.graphql.server;

import com.aquatic.graphql.json.GraphQLJson;
import com.aquatic.graphql.schema.GraphQLProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.GraphQL;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * An embedded GraphQL server: HTTP GET/POST queries and websocket subscriptions at
 * {@code /graphql}, plus an optional GraphiQL UI at {@code /graphiql}.
 *
 * <pre>{@code
 * var provider = GraphQLProvider.from("", GraphQLMetricsListener.NO_OP, new MyService());
 * try (var server = GraphQLServer.builder(provider).port(8080).build()) {
 *     server.start();
 *     server.join();
 * }
 * }</pre>
 *
 * <p>A stopped server cannot be restarted.
 */
public final class GraphQLServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(GraphQLServer.class);

    private final GraphQL graphQL;
    private final ObjectMapper objectMapper;
    private final int port;
    private final boolean graphiqlEnabled;
    private final Duration idleTimeout;
    private Server server;

    private GraphQLServer(Builder builder) {
        this.graphQL = builder.provider.createGraphQL();
        this.objectMapper = builder.objectMapper;
        this.port = builder.port;
        this.graphiqlEnabled = builder.graphiql;
        this.idleTimeout = builder.idleTimeout;
    }

    public static Builder builder(GraphQLProvider provider) {
        return new Builder(provider);
    }

    /** Starts the server. Non-blocking; use {@link #join()} to wait until it stops. */
    public synchronized void start() throws Exception {
        if (server != null) {
            throw new IllegalStateException("GraphQL server already started");
        }
        server = new Server(port);
        ServletContextHandler context = new ServletContextHandler("/");
        server.setHandler(context);
        context.addServlet(new ServletHolder(new GraphQLServlet(graphQL, objectMapper, idleTimeout)), "/graphql");
        if (graphiqlEnabled) {
            context.addServlet(new ServletHolder(new RedirectServlet("/graphiql")), "/");
            context.addServlet(new ServletHolder(new ResourceServlet("/graphiql")), "/graphiql");
        }
        // Required in Jetty 12 to initialize the websocket upgrade machinery.
        JettyWebSocketServletContainerInitializer.configure(context, null);
        server.start();

        URI uri = uri();
        log.info("GraphQL server listening at {}graphql (websocket subscriptions on the same path)", uri);
        if (graphiqlEnabled) {
            log.info("GraphiQL UI available at {}graphiql", uri);
        }
    }

    /** Blocks until the server stops. */
    public void join() throws InterruptedException {
        started().join();
    }

    /** Stops the server gracefully. Safe to call more than once. */
    public synchronized void stop() {
        if (server == null) {
            return;
        }
        try {
            server.stop();
        } catch (Exception e) {
            log.warn("Error stopping GraphQL server", e);
        }
    }

    @Override
    public void close() {
        stop();
    }

    /** The actual bound port (useful with {@code port(0)}). Only valid after {@link #start()}. */
    public int port() {
        return ((ServerConnector) started().getConnectors()[0]).getLocalPort();
    }

    /** The server's base URI. Only valid after {@link #start()}. */
    public URI uri() {
        return URI.create("http://localhost:" + port() + "/");
    }

    private synchronized Server started() {
        if (server == null) {
            throw new IllegalStateException("GraphQL server not started");
        }
        return server;
    }

    public static final class Builder {
        private final GraphQLProvider provider;
        private int port = 8080;
        private ObjectMapper objectMapper = GraphQLJson.defaultMapper();
        private boolean graphiql = true;
        private Duration idleTimeout = Duration.ofSeconds(60);

        private Builder(GraphQLProvider provider) {
            this.provider = Objects.requireNonNull(provider, "provider");
        }

        /** Port to listen on; {@code 0} picks an ephemeral port. Default 8080. */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /** ObjectMapper used for request/response JSON. Default {@link GraphQLJson#defaultMapper()}. */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
            return this;
        }

        /** Whether to serve the GraphiQL UI at {@code /graphiql} (and redirect {@code /} to it). Default true. */
        public Builder graphiql(boolean enabled) {
            this.graphiql = enabled;
            return this;
        }

        /** Websocket idle timeout. Default 60 seconds. */
        public Builder idleTimeout(Duration idleTimeout) {
            this.idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout");
            return this;
        }

        public GraphQLServer build() {
            return new GraphQLServer(this);
        }
    }
}
