package com.aquatic.graphql.schema;

import com.aquatic.graphql.annotations.GraphQLApi;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.leangen.graphql.annotations.GraphQLQuery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Exports a GraphQL schema as SDL from {@link GraphQLApi}-annotated classes discovered by
 * classpath scan — no central service enumeration and no instantiation (services with
 * constructor dependencies need no null-argument hacks).
 *
 * <p>Typical Gradle wiring in a consumer project:
 *
 * <pre>{@code
 * tasks.register('exportGraphQLSchema', JavaExec) {
 *     classpath = sourceSets.main.runtimeClasspath
 *     mainClass = 'com.aquatic.graphql.schema.SchemaExport'
 *     args = ['--package', 'com.example.myapp', '--output', 'schema.graphql']
 * }
 * }</pre>
 *
 * <p>Note: classes with unresolved generic type parameters cannot be exported this way; SPQR
 * rejects them during generation.
 */
public final class SchemaExport {
    private SchemaExport() {}

    /** Finds concrete {@link GraphQLApi}-annotated classes in the given packages, sorted by name. */
    public static List<Class<?>> scanForApis(String... packages) {
        try (ScanResult scan =
                new ClassGraph().acceptPackages(packages).enableAnnotationInfo().scan()) {
            return scan
                    .getClassesWithAnnotation(GraphQLApi.class)
                    .filter(classInfo -> classInfo.isStandardClass()
                            && !classInfo.isAbstract()
                            && !classInfo.isAnonymousInnerClass())
                    .loadClasses()
                    .stream()
                    .sorted(Comparator.comparing(Class::getName))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Builds a schema from the given types without instantiating them. If none of the types
     * defines a query, a placeholder {@code _noop} query is added: GraphQL requires a non-empty
     * query type even in mutation/subscription-only schemas.
     */
    public static GraphQLSchema buildSchema(String prefix, List<Class<?>> apiTypes) {
        if (apiTypes.isEmpty()) {
            throw new IllegalArgumentException("No @GraphQLApi classes to export");
        }
        List<Class<?>> types = new ArrayList<>(apiTypes);
        if (types.stream().noneMatch(SchemaExport::definesQuery)) {
            types.add(NoopQuery.class);
        }
        return GraphQLProvider.fromTypes(prefix, types.toArray(new Class<?>[0])).createSchema();
    }

    /** Scans the given packages, builds the schema, and prints it as SDL. */
    public static String printSchema(String prefix, String... packages) {
        List<Class<?>> apis = scanForApis(packages);
        if (apis.isEmpty()) {
            throw new IllegalArgumentException("No @GraphQLApi classes found in packages " + Arrays.toString(packages));
        }
        return new SchemaPrinter().print(buildSchema(prefix, apis));
    }

    private static boolean definesQuery(Class<?> type) {
        // Mirrors SPQR's AnnotatedResolverBuilder: queries come from annotated methods
        // (including property getters) and public fields.
        return Stream.concat(Arrays.stream(type.getMethods()), Arrays.stream(type.getFields()))
                .anyMatch(element -> element.isAnnotationPresent(GraphQLQuery.class));
    }

    /** Placeholder so mutation/subscription-only schemas validate. No description: keeps SDL diffs clean. */
    static final class NoopQuery {
        @GraphQLQuery(name = "_noop")
        public String noop() {
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        List<String> packages = new ArrayList<>();
        String prefix = "";
        String output = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--package" -> packages.addAll(
                        Arrays.asList(requireValue(args, ++i).split(",")));
                case "--prefix" -> prefix = requireValue(args, ++i);
                case "--output" -> output = requireValue(args, ++i);
                default -> exitUsage("Unknown argument: " + args[i]);
            }
        }
        if (packages.isEmpty()) {
            exitUsage("--package is required");
        }

        String sdl = printSchema(prefix, packages.toArray(new String[0]));
        if (output == null) {
            System.out.print(sdl);
        } else {
            Files.writeString(Path.of(output), sdl);
            System.out.println("Schema exported to: " + output);
        }
    }

    private static String requireValue(String[] args, int index) {
        if (index >= args.length) {
            exitUsage("Missing value for " + args[index - 1]);
        }
        return args[index];
    }

    private static void exitUsage(String error) {
        System.err.println(error);
        System.err.println("Usage: SchemaExport --package <pkg>[,<pkg>...] [--package <pkg>]..."
                + " [--prefix <prefix>] [--output <file>]");
        System.exit(2);
    }
}
