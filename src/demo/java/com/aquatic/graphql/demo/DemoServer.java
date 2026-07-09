package com.aquatic.graphql.demo;

import com.aquatic.graphql.schema.GraphQLProvider;
import com.aquatic.graphql.server.GraphQLServer;

import java.util.Arrays;

/**
 * A runnable demo API: {@code ./gradlew runDemo}, then open http://localhost:8080/graphiql.
 * Pass {@code -metrics} ({@code ./gradlew runDemo -PdemoArgs=-metrics}) to print a metrics
 * summary line to stderr once per second.
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
        boolean metricsEnabled = Arrays.stream(args).anyMatch(arg -> arg.equals("-metrics") || arg.equals("--metrics"));

        var service = new DemoService();
        GraphQLProvider provider;
        if (metricsEnabled) {
            var metrics = new ConsoleMetricsListener();
            metrics.startReporting();
            provider = GraphQLProvider.from("", metrics, service);
        } else {
            provider = GraphQLProvider.from("", service);
        }

        try (var server = GraphQLServer.builder(provider).port(8080).build()) {
            server.start();
            System.out.println();
            System.out.println("Demo GraphQL API running — open http://localhost:8080/graphiql and try:");
            System.out.println("  query        { hello(name: \"aqueduct\") }");
            System.out.println("  mutation     { addItem(value: \"first\") }");
            System.out.println("  query        { items }");
            System.out.println("  subscription { countTo(limit: 5) }");
            System.out.println(
                    metricsEnabled
                            ? "Metrics reporting is ON: one [metrics] line per second on stderr."
                            : "Tip: run with -PdemoArgs=-metrics to print metrics to stderr once per second.");
            System.out.println();
            server.join();
        }
    }
}
