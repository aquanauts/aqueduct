package com.aquatic.graphql.schema;

import io.leangen.graphql.annotations.GraphQLQuery;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLProviderTest {

    public static class FixtureService {
        @GraphQLQuery
        public int takeFive() {
            return 5;
        }
    }

    @Test
    void prefixesTopLevelOperations() {
        var schema = GraphQLProvider.from("Test_", new FixtureService()).createSchema();
        assertNotNull(schema.getQueryType().getFieldDefinition("Test_takeFive"));
        assertNull(schema.getQueryType().getFieldDefinition("takeFive"));
    }

    @Test
    void createGraphQLExecutesQueries() {
        var graphQL = GraphQLProvider.from("", new FixtureService()).createGraphQL();
        assertEquals(Map.of("takeFive", 5), graphQL.execute("{ takeFive }").getData());
    }
}
