package com.aquatic.graphql.metrics;

/**
 * SPI for observing GraphQL operation execution. Implement this to bridge into your metrics
 * registry and pass it to {@code GraphQLProvider.from(...)}.
 *
 * <p>Implementations must be thread-safe: callbacks fire on execution and subscription threads.
 */
public interface GraphQLMetricsListener {
    enum OperationKind {
        QUERY,
        MUTATION,
        SUBSCRIPTION
    }

    /** Called once per top-level resolver invocation (queries, mutations, and subscription setup). */
    default void operationInvoked(String operationName, OperationKind kind) {}

    /** Called when a subscriber attaches to a subscription operation's {@code Publisher}. */
    default void subscriptionStarted(String operationName) {}

    /** Called exactly once per started subscription, on complete, error, or cancel. */
    default void subscriptionEnded(String operationName) {}

    GraphQLMetricsListener NO_OP = new GraphQLMetricsListener() {};
}
