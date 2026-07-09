package com.aquatic.graphql.subscriptions;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A thread-safe {@link Publisher} that broadcasts the latest state to all subscribers.
 * New subscribers immediately receive the current state, if any.
 */
public class StateChangePublisher<T> implements Publisher<T> {
    private final Set<StateSubscription> subscribers = new CopyOnWriteArraySet<>();
    private T state;

    public synchronized void update(T state) {
        this.state = state;
        for (StateSubscription subscription : subscribers) {
            if (subscription.requestCount > 0) {
                subscription.subscriber.onNext(state);
            }
        }
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        StateSubscription subscription = new StateSubscription(s);
        subscribers.add(subscription);
        s.onSubscribe(subscription);
        if (state != null) {
            s.onNext(state);
        }
    }

    private class StateSubscription implements Subscription {
        private final Subscriber<? super T> subscriber;
        private long requestCount;

        public StateSubscription(Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long requestCount) {
            this.requestCount = requestCount;
        }

        @Override
        public void cancel() {
            subscribers.remove(this);
            subscriber.onComplete();
        }
    }
}
