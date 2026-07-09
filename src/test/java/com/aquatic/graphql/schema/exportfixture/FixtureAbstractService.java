package com.aquatic.graphql.schema.exportfixture;

import com.aquatic.graphql.annotations.GraphQLApi;

import io.leangen.graphql.annotations.GraphQLQuery;

/** Abstract classes cannot be operation sources; the scan must skip this. */
@GraphQLApi
public abstract class FixtureAbstractService {
    @GraphQLQuery(name = "abstractQuery")
    public String abstractQuery() {
        return null;
    }
}
