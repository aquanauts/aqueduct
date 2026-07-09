package com.aquatic.graphql.schema.exportfixture;

import com.aquatic.graphql.annotations.GraphQLApi;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLMutation;

/** A service like a constructor-heavy mutation service: no queries at all. */
@GraphQLApi
public class FixtureMutationOnlyService {
    private final Object dependency;

    public FixtureMutationOnlyService(Object dependency) {
        this.dependency = dependency;
    }

    @GraphQLMutation(name = "recordEvent")
    public String recordEvent(@GraphQLArgument(name = "name") String name) {
        return dependency + name;
    }
}
