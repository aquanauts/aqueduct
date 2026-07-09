package com.aquatic.graphql.schema;

import com.aquatic.graphql.schema.exportfixture.FixtureMutationOnlyService;
import com.aquatic.graphql.schema.exportfixture.FixtureQueryService;

import graphql.schema.idl.SchemaPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

class SchemaExportTest {
    private static final String FIXTURE_PACKAGE = "com.aquatic.graphql.schema.exportfixture";

    @Test
    void typeBasedSdlEqualsSingletonBasedSdl() {
        var singletonSdl = new SchemaPrinter()
                .print(GraphQLProvider.from("", new FixtureQueryService()).createSchema());
        var typeSdl = new SchemaPrinter()
                .print(GraphQLProvider.fromTypes("", FixtureQueryService.class).createSchema());
        assertEquals(singletonSdl, typeSdl);
    }

    @Test
    void scanReturnsOnlyConcreteAnnotatedClassesSortedByName() {
        var apis = SchemaExport.scanForApis(FIXTURE_PACKAGE);
        assertEquals(List.of(FixtureMutationOnlyService.class, FixtureQueryService.class), apis);
    }

    @Test
    void dataTypeWithoutGraphQLApiIsNotARootQuery() {
        var sdl = SchemaExport.printSchema("", FIXTURE_PACKAGE);
        assertThat(sdl, containsString("fixtureValue"));
        assertThat(sdl, not(containsString("dataName")));
        assertThat(sdl, not(containsString("abstractQuery")));
    }

    @Test
    void noopInjectedForMutationOnlySchema() {
        var schema = SchemaExport.buildSchema("", List.of(FixtureMutationOnlyService.class));
        assertNotNull(schema.getQueryType().getFieldDefinition("_noop"));
        assertNotNull(schema.getMutationType().getFieldDefinition("recordEvent"));
    }

    @Test
    void noopAbsentWhenAQueryExists() {
        var schema = SchemaExport.buildSchema("", List.of(FixtureMutationOnlyService.class, FixtureQueryService.class));
        assertNull(schema.getQueryType().getFieldDefinition("_noop"));
        assertNotNull(schema.getQueryType().getFieldDefinition("fixtureValue"));
    }

    @Test
    void prefixAppliedToTypeBasedOperations() {
        var schema =
                GraphQLProvider.fromTypes("Test_", FixtureQueryService.class).createSchema();
        assertNotNull(schema.getQueryType().getFieldDefinition("Test_fixtureValue"));
        assertNull(schema.getQueryType().getFieldDefinition("fixtureValue"));
        assertNotNull(schema.getMutationType().getFieldDefinition("Test_setFixtureValue"));
    }

    @Test
    void emptyScanThrows() {
        assertThrows(IllegalArgumentException.class, () -> SchemaExport.printSchema("", "com.aquatic.nosuchpackage"));
    }

    @Test
    void cliWritesSchemaFile(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("schema.graphql");
        SchemaExport.main(new String[] {"--package", FIXTURE_PACKAGE, "--output", output.toString()});
        String sdl = Files.readString(output);
        assertThat(sdl, containsString("fixtureValue"));
        assertThat(sdl, containsString("recordEvent"));
    }
}
