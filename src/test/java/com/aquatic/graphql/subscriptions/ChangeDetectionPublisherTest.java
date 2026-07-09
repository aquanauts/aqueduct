package com.aquatic.graphql.subscriptions;

import graphql.GraphQL;
import io.leangen.graphql.GraphQLSchemaGenerator;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.annotations.GraphQLSubscription;
import io.leangen.graphql.metadata.strategy.query.AnnotatedResolverBuilder;
import io.leangen.graphql.metadata.strategy.value.jackson.JacksonValueMapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ChangeDetectionPublisherTest {

    record TestInteger(int value) implements Keyable {

        @Override
        public String getKey() {
            return String.valueOf(value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestInteger that = (TestInteger) obj;
            return value == that.value;
        }

        @Override
        public String toString() {
            return "TestInteger{" + value + "}";
        }
    }

    record TestItem(int value) implements Keyable {

        @Override
        public String getKey() {
            return String.valueOf(value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestItem testItem = (TestItem) obj;
            return value == testItem.value;
        }
    }

    private List<TestItem> currentData;
    private ChangeDetectionPublisher<TestItem> publisher;

    @Nested
    class SimpleSingleSubscriber {

        @BeforeEach
        void setUp() {
            currentData = new ArrayList<>();
            Supplier<List<TestItem>> dataSupplier = () -> new ArrayList<>(currentData);
            publisher = new ChangeDetectionPublisher<>(dataSupplier);
        }

        @Test
        void testEmptyState() {
            currentData.addAll(List.of());

            List<TestItem> result = publisher.detectChanges(publisher.getLastDataMap(), currentData);
            assertEquals(0, result.size());
        }

        @Test
        void testFirstEmissionWithState() {
            currentData.addAll(Arrays.asList(new TestItem(1), new TestItem(2)));

            List<TestItem> result = publisher.detectChanges(publisher.getLastDataMap(), currentData);

            assertEquals(2, result.size());
            assertTrue(result.contains(new TestItem(1)));
            assertTrue(result.contains(new TestItem(2)));
        }

        @Test
        void testEmptyStateThenAddState() {
            Map<String, TestItem> lastDataMap = publisher.getLastDataMap();
            List<TestItem> result1 = publisher.detectChanges(lastDataMap, currentData);

            assertEquals(0, result1.size());
            assertEquals(0, lastDataMap.size());

            currentData.addAll(Arrays.asList(new TestItem(1), new TestItem(2)));
            List<TestItem> result2 = publisher.detectChanges(lastDataMap, currentData);

            assertEquals(2, result2.size());
            assertTrue(result2.contains(new TestItem(1)));
            assertTrue(result2.contains(new TestItem(2)));
            assertEquals(2, lastDataMap.size());
        }

        @Test
        void testStateUnchangedTwice() {
            currentData.addAll(Arrays.asList(new TestItem(1), new TestItem(2)));

            Map<String, TestItem> lastDataMap = publisher.getLastDataMap();
            List<TestItem> result1 = publisher.detectChanges(lastDataMap, currentData);

            assertEquals(2, result1.size());

            List<TestItem> result2 = publisher.detectChanges(lastDataMap, currentData);
            assertEquals(0, result2.size());

            List<TestItem> result3 = publisher.detectChanges(lastDataMap, currentData);
            assertEquals(0, result3.size());
        }
    }

    static class TestSubscriber implements Subscriber<List<TestInteger>> {
        private final List<List<TestInteger>> receivedPayloads = Collections.synchronizedList(new ArrayList<>());
        private Subscription subscription;
        private volatile Throwable error = null;

        public TestSubscriber() {}

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<TestInteger> payload) {
            receivedPayloads.add(new ArrayList<>(payload));
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onComplete() {}

        public void cancel() {
            if (subscription != null) {
                subscription.cancel();
            }
        }

        public List<List<TestInteger>> getReceivedPayloads() {
            return new ArrayList<>(receivedPayloads);
        }

        public Throwable getError() {
            return error;
        }
    }

    public static class TestSubscriptionService {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final List<TestInteger> data = Collections.synchronizedList(new ArrayList<>());

        Supplier<List<TestInteger>> createIncrementalSupplier() {
            return () -> {
                // Add a new integer each time the supplier is called
                data.add(new TestInteger(counter.getAndIncrement()));
                return new ArrayList<>(data);
            };
        }

        @GraphQLSubscription(name = "integerUpdates", description = "Subscribe to integer updates")
        Publisher<List<TestInteger>> integerUpdates() {
            return new ChangeDetectionPublisher<>(createIncrementalSupplier(), 10);
        }

        // Required: GraphQL schemas must have at least one Query field
        @GraphQLQuery(name = "ping")
        public String ping() {
            return "pong";
        }
    }

    private TestSubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new TestSubscriptionService();

        var schema = new GraphQLSchemaGenerator()
                .withResolverBuilders(new AnnotatedResolverBuilder())
                .withOperationsFromSingleton(subscriptionService)
                .withValueMapperFactory(new JacksonValueMapperFactory())
                .generate();
        GraphQL.newGraphQL(schema).build();
    }

    @Test
    void testSingleSubscriberReceivesPayload() {
        ChangeDetectionPublisher<TestInteger> publisher =
                new ChangeDetectionPublisher<>(subscriptionService.createIncrementalSupplier());

        TestSubscriber subscriber = new TestSubscriber(); // Expect: initial + 2 updates
        publisher.subscribe(subscriber);

        List<List<TestInteger>> payloads = subscriber.getReceivedPayloads();
        assertFalse(payloads.isEmpty());

        assertEquals(1, payloads.get(0).size());
        assertEquals(0, payloads.get(0).get(0).value());
        subscriber.cancel();
    }

    @Test
    void testSecondSubscriberGetsFullPayloadImmediately() {
        ChangeDetectionPublisher<TestInteger> publisher =
                new ChangeDetectionPublisher<>(subscriptionService.createIncrementalSupplier());

        TestSubscriber subscriber1 = new TestSubscriber();
        publisher.subscribe(subscriber1);
        publisher.publishDeltas();
        assertEquals(2, subscriber1.getReceivedPayloads().size()); // 1 at sub start, one at next publish

        // Second subscriber joins
        TestSubscriber subscriber2 = new TestSubscriber();
        publisher.subscribe(subscriber2);
        assertEquals(1, subscriber2.getReceivedPayloads().size());
        assertEquals(2, subscriber2.getReceivedPayloads().get(0).size());

        subscriber1.cancel();
        subscriber2.cancel();
    }

    @Test
    void testBothSubscribersReceiveUpdatesSimultaneously() {
        ChangeDetectionPublisher<TestInteger> publisher =
                new ChangeDetectionPublisher<>(subscriptionService.createIncrementalSupplier());

        TestSubscriber subscriber1 = new TestSubscriber();
        publisher.subscribe(subscriber1);
        publisher.publishDeltas();
        publisher.publishDeltas();

        // Second subscriber joins
        TestSubscriber subscriber2 = new TestSubscriber();
        publisher.subscribe(subscriber2);

        publisher.publishDeltas();
        assertEquals(last(subscriber1.getReceivedPayloads()), last(subscriber2.getReceivedPayloads()));

        subscriber1.cancel();
        subscriber2.cancel();
    }

    @Test
    void testOneDisconnectsOtherContinues() {
        ChangeDetectionPublisher<TestInteger> publisher =
                new ChangeDetectionPublisher<>(subscriptionService.createIncrementalSupplier());

        TestSubscriber subscriber1 = new TestSubscriber();
        publisher.subscribe(subscriber1);
        publisher.publishDeltas();
        publisher.publishDeltas();

        // Second subscriber joins
        TestSubscriber subscriber2 = new TestSubscriber();
        publisher.subscribe(subscriber2);

        publisher.publishDeltas();

        var sub1NumPayloads = subscriber1.getReceivedPayloads().size();
        var sub2NumPayloads = subscriber2.getReceivedPayloads().size();

        subscriber1.cancel();

        publisher.publishDeltas();
        assertEquals(sub1NumPayloads, subscriber1.getReceivedPayloads().size());
        assertEquals(sub2NumPayloads + 1, subscriber2.getReceivedPayloads().size());

        subscriber2.cancel();
    }

    @Test
    void testAllDisconnectsThenReconnects() {
        ChangeDetectionPublisher<TestInteger> publisher =
                new ChangeDetectionPublisher<>(subscriptionService.createIncrementalSupplier());

        TestSubscriber subscriber1 = new TestSubscriber();
        publisher.subscribe(subscriber1);
        publisher.publishDeltas();
        publisher.publishDeltas();

        subscriber1.cancel();

        publisher.subscribe(subscriber1);
        publisher.publishDeltas();

        var p = subscriber1.getReceivedPayloads();
        publisher.publishDeltas();
    }

    private static <T> T last(List<T> list) {
        return list.get(list.size() - 1);
    }
}
