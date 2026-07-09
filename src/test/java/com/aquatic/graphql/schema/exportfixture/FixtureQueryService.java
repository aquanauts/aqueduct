package com.aquatic.graphql.schema.exportfixture;

import com.aquatic.graphql.annotations.GraphQLApi;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;

@GraphQLApi
public class FixtureQueryService {
    @GraphQLQuery(name = "fixtureValue")
    public int fixtureValue() {
        return 42;
    }

    @GraphQLMutation(name = "setFixtureValue")
    public int setFixtureValue(@GraphQLArgument(name = "value") int value) {
        return value;
    }
}
