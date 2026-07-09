package com.aquatic.graphql.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serves GraphQL over HTTP GET/POST and upgrades websocket connections for subscriptions.
 * Websocket upgrade requests are handled by {@link JettyWebSocketServlet#service} before
 * they can reach the {@code doGet}/{@code doPost} handlers below.
 */
@SuppressWarnings("serial")
class GraphQLServlet extends JettyWebSocketServlet {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final GraphQL graphQL;
    private final ObjectMapper objectMapper;
    private final Duration idleTimeout;

    GraphQLServlet(GraphQL graphQL, ObjectMapper objectMapper, Duration idleTimeout) {
        this.graphQL = graphQL;
        this.objectMapper = objectMapper;
        this.idleTimeout = idleTimeout;
    }

    @Override
    protected void configure(JettyWebSocketServletFactory factory) {
        factory.setIdleTimeout(idleTimeout);
        factory.setCreator((req, resp) -> {
            // Check for both graphql-transport-ws (newer) and graphql-ws (older) subprotocols
            if (req.getSubProtocols().contains("graphql-transport-ws")) {
                resp.setAcceptedSubProtocol("graphql-transport-ws");
            } else if (req.getSubProtocols().contains("graphql-ws")) {
                resp.setAcceptedSubProtocol("graphql-ws");
            }
            return new GraphQLWebSocketHandler(graphQL, objectMapper);
        });
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String query = req.getParameter("query");
        String variables = req.getParameter("variables");
        String operationName = req.getParameter("operationName");

        if (query != null) {
            executeGraphQLQuery(query, variables, operationName, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"errors\":[{\"message\":\"No query provided\"}]}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String requestBody = req.getReader().lines().collect(Collectors.joining("\n"));
            Map<String, Object> request = objectMapper.readValue(requestBody, MAP_TYPE);

            String query = (String) request.get("query");
            String variables =
                    request.get("variables") != null ? objectMapper.writeValueAsString(request.get("variables")) : null;
            String operationName = (String) request.get("operationName");

            executeGraphQLQuery(query, variables, operationName, resp);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"errors\":[{\"message\":\"" + e.getMessage() + "\"}]}");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private void executeGraphQLQuery(String query, String variablesJson, String operationName, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Enable CORS
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");

        try {
            Map<String, Object> variables = Map.of();
            if (variablesJson != null && !variablesJson.trim().isEmpty()) {
                variables = objectMapper.readValue(variablesJson, MAP_TYPE);
            }

            ExecutionInput.Builder executionInputBuilder =
                    ExecutionInput.newExecutionInput().query(query).variables(variables);

            if (operationName != null) {
                executionInputBuilder.operationName(operationName);
            }

            ExecutionInput executionInput = executionInputBuilder.build();
            ExecutionResult result = graphQL.execute(executionInput);

            resp.getWriter().write(objectMapper.writeValueAsString(result.toSpecification()));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"errors\":[{\"message\":\"GraphQL execution error: " + e.getMessage() + "\"}]}");
        }
    }
}
