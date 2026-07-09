package com.aquatic.graphql.demo;

import com.aquatic.graphql.schema.GraphQLProvider;
import com.aquatic.graphql.server.GraphQLServer;

/**
 * A runnable demo API: {@code ./gradlew runDemo}, then open http://localhost:8080/graphiql.
 *
 * <p>Things to try in GraphiQL:
 *
 * <pre>
 * query        { hello(name: "aqueduct") }
 * mutation     { addItem(value: "first") }
 * query        { items }
 * subscription { countTo(limit: 5) }
 * </pre>
 */
public final class DemoServer {
    private DemoServer() {}

    public static void main(String[] args) throws Exception {
        var provider = GraphQLProvider.from("", new DemoService());
        try (var server = GraphQLServer.builder(provider).port(8080).build()) {
            server.start();
            System.out.println();
            System.out.println("Demo GraphQL API running — open http://localhost:8080/graphiql and try:");
            System.out.println("  query        { hello(name: \"aqueduct\") }");
            System.out.println("  mutation     { addItem(value: \"first\") }");
            System.out.println("  query        { items }");
            System.out.println("  subscription { countTo(limit: 5) }");
            System.out.println();
            server.join();
        }
    }
}
