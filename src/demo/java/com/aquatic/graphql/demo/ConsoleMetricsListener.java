package com.aquatic.graphql.demo;

import com.aquatic.graphql.metrics.GraphQLMetricsListener;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class ConsoleMetricsListener implements GraphQLMetricsListener {
    private final AtomicLong queries = new AtomicLong();
    private final AtomicLong mutations = new AtomicLong();
    private final AtomicLong subscriptionCalls = new AtomicLong();
    private final AtomicLong activeSubscriptions = new AtomicLong();
    private final Map<String, AtomicLong> byOperation = new ConcurrentHashMap<>();

    @Override
    public void operationInvoked(String operationName, OperationKind kind) {
        switch (kind) {
            case QUERY -> queries.incrementAndGet();
            case MUTATION -> mutations.incrementAndGet();
            case SUBSCRIPTION -> subscriptionCalls.incrementAndGet();
        }
        byOperation.computeIfAbsent(operationName, name -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public void subscriptionStarted(String operationName) {
        activeSubscriptions.incrementAndGet();
    }

    @Override
    public void subscriptionEnded(String operationName) {
        activeSubscriptions.decrementAndGet();
    }

    /** Prints one summary line per second to stderr from a daemon thread. */
    void startReporting() {
        var executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "demo-metrics");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::report, 1, 1, TimeUnit.SECONDS);
    }

    private void report() {
        System.err.printf(
                "[metrics] queries=%d mutations=%d subscriptionCalls=%d activeSubscriptions=%d byOperation=%s%n",
                queries.get(),
                mutations.get(),
                subscriptionCalls.get(),
                activeSubscriptions.get(),
                new TreeMap<>(byOperation));
    }
}
