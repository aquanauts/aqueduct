package com.aquatic.graphql.demo;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.annotations.GraphQLSubscription;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** The resolvers behind the demo API. */
public class DemoService {
    private final List<String> items = new CopyOnWriteArrayList<>();

    @GraphQLQuery(name = "hello", description = "A basic query")
    public String hello(@GraphQLArgument(name = "name") String name) {
        return "Hello, " + (name != null ? name : "world") + "!";
    }

    @GraphQLQuery(name = "items", description = "The in-memory list, populated by addItem")
    public List<String> items() {
        return List.copyOf(items);
    }

    @GraphQLMutation(name = "addItem", description = "Adds a string to the in-memory list and returns the list")
    public List<String> addItem(@GraphQLArgument(name = "value") String value) {
        items.add(value);
        return List.copyOf(items);
    }

    @GraphQLSubscription(name = "countTo", description = "Emits 1..limit, one per second, then completes")
    public Publisher<Integer> countTo(@GraphQLArgument(name = "limit") int limit) {
        return subscriber -> {
            var executor = Executors.newSingleThreadScheduledExecutor();
            var count = new AtomicInteger();
            var task = new AtomicReference<ScheduledFuture<?>>();
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    // The demo emits on its own schedule regardless of demand.
                }

                @Override
                public void cancel() {
                    ScheduledFuture<?> future = task.get();
                    if (future != null) {
                        future.cancel(false);
                    }
                    executor.shutdown();
                }
            });
            task.set(executor.scheduleAtFixedRate(
                    () -> {
                        int current = count.incrementAndGet();
                        if (current <= limit) {
                            subscriber.onNext(current);
                        }
                        if (current >= limit) {
                            subscriber.onComplete();
                            task.get().cancel(false);
                            executor.shutdown();
                        }
                    },
                    1,
                    1,
                    TimeUnit.SECONDS));
        };
    }
}
