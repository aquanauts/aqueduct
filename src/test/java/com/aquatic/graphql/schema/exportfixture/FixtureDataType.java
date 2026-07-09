package com.aquatic.graphql.schema.exportfixture;

import io.leangen.graphql.annotations.GraphQLQuery;

/** A data type that annotates a getter for field naming — NOT a service, so no @GraphQLApi. */
public class FixtureDataType {
    @GraphQLQuery(name = "dataName")
    public String dataName() {
        return "data";
    }
}
