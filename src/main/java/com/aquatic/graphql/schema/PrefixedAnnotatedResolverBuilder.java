package com.aquatic.graphql.schema;

import io.leangen.graphql.metadata.Resolver;
import io.leangen.graphql.metadata.strategy.query.AnnotatedResolverBuilder;
import io.leangen.graphql.metadata.strategy.query.ResolverBuilder;
import io.leangen.graphql.metadata.strategy.query.ResolverBuilderParams;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * A SPQR {@link ResolverBuilder} that prepends a prefix (e.g. {@code "MyApp_"}) to all
 * top-level query, mutation, and subscription field names.
 */
public class PrefixedAnnotatedResolverBuilder implements ResolverBuilder {

    private final AnnotatedResolverBuilder delegate;
    private final String prefix;

    public PrefixedAnnotatedResolverBuilder(String prefix) {
        this.delegate = new AnnotatedResolverBuilder();
        this.prefix = prefix != null ? prefix : "";
    }

    @Override
    public Collection<Resolver> buildQueryResolvers(ResolverBuilderParams params) {
        Collection<Resolver> resolvers = delegate.buildQueryResolvers(params);
        // Only prefix top-level query resolvers
        return addPrefixToTopLevelResolvers(resolvers, true);
    }

    @Override
    public Collection<Resolver> buildMutationResolvers(ResolverBuilderParams params) {
        Collection<Resolver> resolvers = delegate.buildMutationResolvers(params);
        // Only prefix top-level mutation resolvers
        return addPrefixToTopLevelResolvers(resolvers, true);
    }

    @Override
    public Collection<Resolver> buildSubscriptionResolvers(ResolverBuilderParams params) {
        Collection<Resolver> resolvers = delegate.buildSubscriptionResolvers(params);
        // Only prefix top-level subscription resolvers
        return addPrefixToTopLevelResolvers(resolvers, true);
    }

    Collection<Resolver> addPrefixToTopLevelResolvers(Collection<Resolver> resolvers, boolean isTopLevel) {
        if (!isTopLevel) {
            return resolvers;
        }
        return resolvers.stream().map(this::createPrefixedResolver).collect(Collectors.toList());
    }

    Resolver createPrefixedResolver(Resolver originalResolver) {
        try {
            Field fieldNameField = findFieldNameField(originalResolver.getClass());
            if (fieldNameField != null) {
                fieldNameField.setAccessible(true);
                String originalName = (String) fieldNameField.get(originalResolver);

                if (originalName != null && !originalName.startsWith(prefix)) {
                    fieldNameField.set(originalResolver, prefix + originalName);
                }
            }
            return originalResolver;
        } catch (Exception e) {
            return originalResolver;
        }
    }

    private Field findFieldNameField(Class<?> clazz) {
        // Look for field names that likely hold the GraphQL field name
        // Updated based on GraphQL-SPQR's actual field names
        String[] possibleNames = {
            "fieldName", // Most likely in GraphQL-SPQR
            "name",
            "operationName",
            "queryName",
            "field",
            "graphQLFieldName"
        };

        for (String fieldName : possibleNames) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                if (field.getType() == String.class) {
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
                // Continue searching
            }
        }

        // Search in parent classes
        if (clazz.getSuperclass() != null && !clazz.getSuperclass().equals(Object.class)) {
            return findFieldNameField(clazz.getSuperclass());
        }

        return null;
    }
}
