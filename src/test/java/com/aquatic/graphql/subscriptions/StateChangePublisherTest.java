package com.aquatic.graphql.subscriptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

class StateChangePublisherTest {
    private StateChangePublisher<String> publisher;
    private StringSubscriber subscriber;

    @BeforeEach
    public void beforeEach() {
        publisher = new StateChangePublisher<>();
        subscriber = new StringSubscriber();
    }

    @Test
    public void canUpdateState() {
        publisher.subscribe(subscriber);
        publisher.update("hello");
        publisher.update("world");
        assertEquals(List.of("hello", "world"), subscriber.updates);
    }

    @Test
    public void shouldPublishStateOnSubscription() {
        publisher.update("hi there");
        publisher.subscribe(subscriber);
        assertEquals(List.of("hi there"), subscriber.updates);
    }

    @Test
    public void canRemoveSubscriberOnCancel() {
        publisher.subscribe(subscriber);
        subscriber.subscription.cancel();
        publisher.update("hello?");
        assertEquals(emptyList(), subscriber.updates);
        assertTrue(subscriber.complete);
    }

    @Test
    public void shouldNotPublishIfRequestCountIsZero() {
        publisher.subscribe(subscriber);
        subscriber.subscription.request(0);
        publisher.update("hello?");
        assertEquals(emptyList(), subscriber.updates);
    }

    private static class StringSubscriber implements Subscriber<String> {
        public List<String> updates = new ArrayList<>();
        private boolean complete;
        private Subscription subscription;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String s) {
            updates.add(s);
        }

        @Override
        public void onError(Throwable t) {}

        @Override
        public void onComplete() {
            complete = true;
        }
    }
}
