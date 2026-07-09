package com.aquatic.graphql.server;

import com.aquatic.graphql.json.GraphQLJson;
import com.aquatic.graphql.schema.GraphQLProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

class GraphQLServerTest {
    private static final ObjectMapper mapper = GraphQLJson.defaultMapper();

    private GraphQLServer server;
    private HttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = GraphQLServer.builder(GraphQLProvider.from("", new BrubeckService()))
                .port(0)
                .build();
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void shouldFailBeforeStart() {
        var unstarted = GraphQLServer.builder(GraphQLProvider.from("", new BrubeckService()))
                .port(0)
                .build();
        assertThrows(IllegalStateException.class, unstarted::port);
    }

    @Test
    void shouldBindEphemeralPort() {
        assertTrue(server.port() > 0);
        assertEquals(URI.create("http://localhost:" + server.port() + "/"), server.uri());
    }

    @Test
    void canExecuteQueryAsGet() throws Exception {
        var response = get("/graphql?query=" + urlEncode("{takeFive}"));
        assertEquals(200, response.statusCode());
        assertEquals(Map.of("takeFive", 5), data(response));
    }

    @Test
    void canExecuteQueryWithVariablesAsGet() throws Exception {
        var query = "query Add($a: Int!, $b: Int!) { add(a: $a, b: $b) }";
        var variables = "{\"a\": 2, \"b\": 3}";
        var response = get("/graphql?query=" + urlEncode(query) + "&variables=" + urlEncode(variables));
        assertEquals(200, response.statusCode());
        assertEquals(Map.of("add", 5), data(response));
    }

    @Test
    void canExecuteQueryAsPost() throws Exception {
        var body = mapper.writeValueAsString(Map.of(
                "query", "query Add($a: Int!, $b: Int!) { add(a: $a, b: $b) }", "variables", Map.of("a", 20, "b", 3)));
        var response = client.send(
                HttpRequest.newBuilder(server.uri().resolve("/graphql"))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(Map.of("add", 23), data(response));
    }

    @Test
    void shouldReturn400WhenNoQueryProvided() throws Exception {
        var response = get("/graphql");
        assertEquals(400, response.statusCode());
        assertThat(response.body(), containsString("No query provided"));
    }

    @Test
    void shouldReturn500OnUnparseablePostBody() throws Exception {
        var response = client.send(
                HttpRequest.newBuilder(server.uri().resolve("/graphql"))
                        .POST(HttpRequest.BodyPublishers.ofString("this is not json"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(500, response.statusCode());
    }

    @Test
    void shouldAnswerCorsPreflight() throws Exception {
        var response = client.send(
                HttpRequest.newBuilder(server.uri().resolve("/graphql"))
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(
                "*",
                response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }

    @Test
    void shouldRedirectRootToGraphiql() throws Exception {
        var response = get("/");
        assertEquals(302, response.statusCode());
        assertThat(response.headers().firstValue("Location").orElse(""), containsString("/graphiql"));
    }

    @Test
    void shouldServeGraphiql() throws Exception {
        var response = get("/graphiql");
        assertEquals(200, response.statusCode());
        assertThat(response.body(), containsString("GraphiQL"));
    }

    @Test
    void canDisableGraphiql() throws Exception {
        try (var bare = GraphQLServer.builder(GraphQLProvider.from("", new BrubeckService()))
                .port(0)
                .graphiql(false)
                .build()) {
            bare.start();
            var response = client.send(
                    HttpRequest.newBuilder(bare.uri().resolve("/graphiql"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        }
    }

    @Test
    void stopIsIdempotent() {
        server.stop();
        server.stop();
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder(server.uri().resolve(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Object data(HttpResponse<String> response) throws IOException {
        return mapper.readValue(response.body(), Map.class).get("data");
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
