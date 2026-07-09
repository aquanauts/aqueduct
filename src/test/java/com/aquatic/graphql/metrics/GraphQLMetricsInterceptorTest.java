package com.aquatic.graphql.metrics;

import com.aquatic.graphql.metrics.GraphQLMetricsListener.OperationKind;
import com.aquatic.graphql.schema.GraphQLProvider;
import com.aquatic.graphql.subscriptions.StateChangePublisher;

import graphql.ExecutionResult;
import graphql.GraphQL;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.annotations.GraphQLSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLMetricsInterceptorTest {

    static class RecordingListener implements GraphQLMetricsListener {
        final List<String> invocations = new CopyOnWriteArrayList<>();
        final AtomicInteger started = new AtomicInteger();
        final AtomicInteger ended = new AtomicInteger();

        @Override
        public void operationInvoked(String operationName, OperationKind kind) {
            invocations.add(operationName + ":" + kind);
        }

        @Override
        public void subscriptionStarted(String operationName) {
            started.incrementAndGet();
        }

        @Override
        public void subscriptionEnded(String operationName) {
            ended.incrementAndGet();
        }
    }

    public static class TestService {
        final StateChangePublisher<String> updates = new StateChangePublisher<>();
        String stored = "initial";

        @GraphQLQuery
        public String stored() {
            return stored;
        }

        @GraphQLMutation
        public String store(@GraphQLArgument(name = "value") String value) {
            stored = value;
            return stored;
        }

        @GraphQLSubscription(name = "updates")
        public Publisher<String> updates() {
            return updates;
        }
    }

    private RecordingListener listener;
    private GraphQL graphQL;

    @BeforeEach
    void setUp() {
        listener = new RecordingListener();
        graphQL = GraphQLProvider.from("", listener, new TestService()).createGraphQL();
    }

    @Test
    void reportsQueryInvocations() {
        var result = graphQL.execute("{ stored }");
        assertEquals(Map.of("stored", "initial"), result.getData());
        assertEquals(List.of("stored:QUERY"), listener.invocations);
    }

    @Test
    void reportsMutationInvocations() {
        var result = graphQL.execute("mutation { store(value: \"changed\") }");
        assertEquals(Map.of("store", "changed"), result.getData());
        assertEquals(List.of("store:MUTATION"), listener.invocations);
    }

    @Test
    void reportsSubscriptionLifecycle() {
        ExecutionResult result = graphQL.execute("subscription { updates }");
        Publisher<Object> publisher = result.getData();
        assertNotNull(publisher);
        assertEquals(List.of("updates:SUBSCRIPTION"), listener.invocations);
        assertEquals(0, listener.started.get());

        var subscriber = new CollectingSubscriber();
        publisher.subscribe(subscriber);
        assertEquals(1, listener.started.get());
        assertEquals(0, listener.ended.get());

        subscriber.subscription.cancel();
        assertEquals(1, listener.ended.get());

        // Cancelling twice must not double-report
        subscriber.subscription.cancel();
        assertEquals(1, listener.ended.get());
    }

    @Test
    void countsConcurrentSubscriptions() {
        ExecutionResult result = graphQL.execute("subscription { updates }");
        Publisher<Object> publisher = result.getData();

        var first = new CollectingSubscriber();
        var second = new CollectingSubscriber();
        publisher.subscribe(first);
        publisher.subscribe(second);
        assertEquals(2, listener.started.get());

        first.subscription.cancel();
        assertEquals(1, listener.ended.get());
        second.subscription.cancel();
        assertEquals(2, listener.ended.get());
    }

    private static class CollectingSubscriber implements Subscriber<Object> {
        final List<Object> items = new ArrayList<>();
        Subscription subscription;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Object item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable t) {}

        @Override
        public void onComplete() {}
    }
}
