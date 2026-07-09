package com.aquatic.graphql.metrics;

import com.aquatic.graphql.metrics.GraphQLMetricsListener.OperationKind;

import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.annotations.GraphQLSubscription;
import io.leangen.graphql.execution.InvocationContext;
import io.leangen.graphql.execution.ResolverInterceptor;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * SPQR {@link ResolverInterceptor} that reports top-level operation invocations to a
 * {@link GraphQLMetricsListener} and wraps subscription publishers so subscription
 * lifecycles are reported too.
 */
public class GraphQLMetricsInterceptor implements ResolverInterceptor {
    private static final Logger log = LoggerFactory.getLogger(GraphQLMetricsInterceptor.class);

    private final GraphQLMetricsListener listener;

    public GraphQLMetricsInterceptor(GraphQLMetricsListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public Object aroundInvoke(InvocationContext context, Continuation continuation) throws Exception {
        if (!isGraphQLOperation(context)) {
            return continuation.proceed(context);
        }

        String operationName = extractOperationName(context);
        if (operationName == null) {
            // Fallback: just proceed if we can't get operation name
            return continuation.proceed(context);
        }

        if (isAnnotatedWith(context, GraphQLSubscription.class)) {
            listener.operationInvoked(operationName, OperationKind.SUBSCRIPTION);
            Object result = continuation.proceed(context);

            if (result instanceof Publisher) {
                return new MetricsTrackingPublisher<>((Publisher<?>) result, operationName, listener);
            }
            return result;
        } else {
            OperationKind kind =
                    isAnnotatedWith(context, GraphQLMutation.class) ? OperationKind.MUTATION : OperationKind.QUERY;
            listener.operationInvoked(operationName, kind);
            return continuation.proceed(context);
        }
    }

    private String extractOperationName(InvocationContext context) {
        try {
            return context.getResolver().getOperationName();
        } catch (Exception e) {
            log.warn("Failed to extract operation name from InvocationContext", e);
            return null;
        }
    }

    private boolean isGraphQLOperation(InvocationContext context) {
        return isAnnotatedWith(context, GraphQLQuery.class)
                || isAnnotatedWith(context, GraphQLMutation.class)
                || isAnnotatedWith(context, GraphQLSubscription.class);
    }

    private boolean isAnnotatedWith(
            InvocationContext context, Class<? extends java.lang.annotation.Annotation> annotation) {
        try {
            Object delegate = context.getResolver().getExecutable().getDelegate();
            if (delegate instanceof Method method) {
                return method.isAnnotationPresent(annotation);
            }
        } catch (Exception e) {
            log.warn("Failed to check GraphQL operation annotations", e);
        }
        return false;
    }
}
