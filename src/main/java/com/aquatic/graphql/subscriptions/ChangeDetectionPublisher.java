package com.aquatic.graphql.subscriptions;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class ChangeDetectionPublisher<T extends Keyable> implements Publisher<List<T>> {
    // IMPORTANT NOTE: in its current implementation, this class only handles datasets that largely have the same
    // keys at all times (or add keys through the day). it does not handle the removal of keys.

    private static final Logger log = LoggerFactory.getLogger(ChangeDetectionPublisher.class);

    private final Supplier<List<T>> dataSupplier;
    private final long intervalTime;
    private final TimeUnit intervalTimeUnit;
    private final ScheduledExecutorService scheduler;
    private final boolean useCustomScheduler;

    private final Set<Subscriber<? super List<T>>> subscribers = new HashSet<>();
    private final Map<String, T> lastDataMap = new ConcurrentHashMap<>();
    private ScheduledFuture<?> publisherTask;
    private List<T> currentFullData = new ArrayList<>();

    public ChangeDetectionPublisher(
            Supplier<List<T>> dataSupplier,
            long intervalTime,
            TimeUnit timeUnit,
            ScheduledExecutorService scheduler,
            boolean useCustomScheduler) {
        this.dataSupplier = dataSupplier;
        this.intervalTime = intervalTime;
        this.intervalTimeUnit = timeUnit;
        this.scheduler = scheduler;
        this.useCustomScheduler = useCustomScheduler;
    }

    public ChangeDetectionPublisher(Supplier<List<T>> dataSupplier, long intervalMillis) {
        this(dataSupplier, intervalMillis, TimeUnit.MILLISECONDS, Executors.newSingleThreadScheduledExecutor(), false);
    }

    ChangeDetectionPublisher(Supplier<List<T>> dataSupplier) {
        this(dataSupplier, 1, TimeUnit.MILLISECONDS, null, true);
    }

    @Override
    public synchronized void subscribe(Subscriber<? super List<T>> subscriber) {
        subscribers.add(subscriber);

        // Start the scheduled task if this is the first subscriber
        if (subscribers.size() == 1 && publisherTask == null) {
            startPublishing();
        }

        subscriber.onNext(currentFullData);
        subscriber.onSubscribe(new SyncedSubscription(subscriber));
    }

    private void startPublishing() {
        updateCurrentData();
        if (!useCustomScheduler) {
            publisherTask =
                    scheduler.scheduleAtFixedRate(this::publishDeltas, intervalTime, intervalTime, intervalTimeUnit);
        }
    }

    private void updateCurrentData() {
        currentFullData = dataSupplier.get();
        currentFullData.forEach(item -> lastDataMap.put(item.getKey(), item));
    }

    public synchronized void publishDeltas() {
        try {
            if (subscribers.isEmpty()) {
                return;
            }

            List<T> newData = dataSupplier.get();
            List<T> deltas = detectChanges(lastDataMap, newData);

            if (!deltas.isEmpty()) {
                currentFullData = newData;

                // Publish deltas to all subscribers
                for (Subscriber<? super List<T>> subscriber : subscribers) {
                    try {
                        subscriber.onNext(new ArrayList<>(deltas));
                    } catch (Exception e) {
                        // Remove failed subscriber
                        removeSubscriber(subscriber);
                        subscriber.onError(e);
                    }
                }
            }
        } catch (Exception e) {
            notifyError(e);
        }
    }

    List<T> detectChanges(Map<String, T> lastData, List<T> currentData) {
        List<T> changedItems = new ArrayList<>();

        for (T current : currentData) {
            String key = current.getKey();
            T previous = lastData.get(key);

            if (previous == null || !previous.equals(current)) {
                changedItems.add(current);
            }
        }

        for (T item : changedItems) {
            lastDataMap.put(item.getKey(), item);
        }

        return changedItems;
    }

    private synchronized void removeSubscriber(Subscriber<? super List<T>> subscriber) {
        subscribers.remove(subscriber);
    }

    private void notifyError(Exception e) {
        Set<Subscriber<? super List<T>>> currentSubscribers = new HashSet<>(subscribers);
        for (Subscriber<? super List<T>> subscriber : currentSubscribers) {
            try {
                removeSubscriber(subscriber);
                subscriber.onError(e);
            } catch (Exception ignored) {
                log.info("Error sending subscription payload", e);
            }
        }
    }

    Map<String, T> getLastDataMap() {
        return lastDataMap;
    }

    private class SyncedSubscription implements Subscription {
        private final Subscriber<? super List<T>> subscriber;
        private volatile boolean cancelled = false;

        public SyncedSubscription(Subscriber<? super List<T>> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                subscriber.onError(new IllegalArgumentException("Request must be positive"));
            }
            // Request handling is managed by the publisher itself
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                cancelled = true;
                removeSubscriber(subscriber);
            }
        }
    }
}
