package com.aquatic.graphql.metrics;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Wraps a subscription's {@link Publisher} to report subscription start and (exactly-once)
 * end to the {@link GraphQLMetricsListener}.
 */
class MetricsTrackingPublisher<T> implements Publisher<T> {
    private final Publisher<T> delegate;
    private final String operationName;
    private final GraphQLMetricsListener listener;

    MetricsTrackingPublisher(Publisher<T> delegate, String operationName, GraphQLMetricsListener listener) {
        this.delegate = delegate;
        this.operationName = operationName;
        this.listener = listener;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        listener.subscriptionStarted(operationName);
        delegate.subscribe(new MetricsTrackingSubscriber<>(subscriber, operationName, listener));
    }

    private static class MetricsTrackingSubscriber<T> implements Subscriber<T> {
        private final Subscriber<? super T> delegate;
        private final String operationName;
        private final GraphQLMetricsListener listener;
        private volatile boolean completed = false;

        MetricsTrackingSubscriber(
                Subscriber<? super T> delegate, String operationName, GraphQLMetricsListener listener) {
            this.delegate = delegate;
            this.operationName = operationName;
            this.listener = listener;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            delegate.onSubscribe(new MetricsTrackingSubscription(subscription));
        }

        @Override
        public void onNext(T item) {
            delegate.onNext(item);
        }

        @Override
        public void onError(Throwable t) {
            endIfNeeded();
            delegate.onError(t);
        }

        @Override
        public void onComplete() {
            endIfNeeded();
            delegate.onComplete();
        }

        private void endIfNeeded() {
            if (!completed) {
                completed = true;
                listener.subscriptionEnded(operationName);
            }
        }

        private class MetricsTrackingSubscription implements Subscription {
            private final Subscription delegate;

            MetricsTrackingSubscription(Subscription delegate) {
                this.delegate = delegate;
            }

            @Override
            public void request(long n) {
                delegate.request(n);
            }

            @Override
            public void cancel() {
                endIfNeeded();
                delegate.cancel();
            }
        }
    }
}
