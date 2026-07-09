package com.aquatic.graphql.server;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLQuery;

public class BrubeckService {
    @GraphQLQuery
    public int takeFive() {
        return 5;
    }

    @GraphQLQuery
    public int add(@GraphQLArgument(name = "a") int a, @GraphQLArgument(name = "b") int b) {
        return a + b;
    }
}
