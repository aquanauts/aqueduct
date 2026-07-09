package com.aquatic.graphql.schema;

import com.aquatic.graphql.metrics.GraphQLMetricsInterceptor;
import com.aquatic.graphql.metrics.GraphQLMetricsListener;

import graphql.GraphQL;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.schema.GraphQLSchema;
import io.leangen.graphql.GraphQLSchemaGenerator;
import io.leangen.graphql.execution.ResolverInterceptor;
import io.leangen.graphql.metadata.strategy.value.jackson.JacksonValueMapperFactory;

/**
 * Provides the {@link GraphQLSchema} (and, by default, the {@link GraphQL} execution engine)
 * for a {@code GraphQLServer}. Build one from annotated singletons with {@link #from}.
 */
@FunctionalInterface
public interface GraphQLProvider {

    GraphQLSchema createSchema();

    default GraphQL createGraphQL() {
        return GraphQL.newGraphQL(createSchema())
                .queryExecutionStrategy(new AsyncExecutionStrategy())
                .mutationExecutionStrategy(new AsyncExecutionStrategy())
                .subscriptionExecutionStrategy(new SubscriptionExecutionStrategy())
                .build();
    }

    /** Builds a provider from SPQR-annotated singletons, without metrics. */
    static GraphQLProvider from(String prefix, Object... singletons) {
        return from(prefix, (ResolverInterceptor) null, singletons);
    }

    /** Builds a provider that reports operation metrics to the given listener. */
    static GraphQLProvider from(String prefix, GraphQLMetricsListener metricsListener, Object... singletons) {
        ResolverInterceptor interceptor =
                metricsListener != null ? new GraphQLMetricsInterceptor(metricsListener) : null;
        return from(prefix, interceptor, singletons);
    }

    /** Escape hatch: builds a provider with a custom SPQR resolver interceptor. */
    static GraphQLProvider from(String prefix, ResolverInterceptor interceptor, Object... singletons) {
        var generator = new GraphQLSchemaGenerator()
                .withResolverBuilders(new PrefixedAnnotatedResolverBuilder(prefix))
                .withValueMapperFactory(new JacksonValueMapperFactory());

        if (interceptor != null) {
            generator.withResolverInterceptors(interceptor);
        }

        for (var singleton : singletons) {
            generator.withOperationsFromSingleton(singleton);
        }
        return generator::generate;
    }
}
