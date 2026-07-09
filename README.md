# aqueduct

An embedded GraphQL HTTP + WebSocket server for Java, built on
[graphql-java](https://github.com/graphql-java/graphql-java),
[GraphQL-SPQR](https://github.com/leangen/graphql-spqr) and Jetty 12.

- **Code-first schemas** — annotate plain Java classes with SPQR's
  `@GraphQLQuery` / `@GraphQLMutation` / `@GraphQLSubscription`; the schema is generated for you.
- **HTTP GET and POST** query execution with CORS support.
- **Subscriptions over WebSocket** — supports both the modern `graphql-transport-ws`
  and legacy `graphql-ws` subprotocols.
- **GraphiQL UI** ([GraphQL Yoga](https://the-guild.dev/graphql/yoga-server)'s flavor) served at `/graphiql` (optional).
- **Operation-name prefixing** for schema federation/namespacing.
- **Metrics SPI** — plug in your own metrics registry via `GraphQLMetricsListener`.
- **Reactive-streams publishers** for subscription feeds: `StateChangePublisher`
  (broadcast latest state) and `ChangeDetectionPublisher` (poll a supplier, emit deltas).

Requires **Java 21+**.

## Quick start

```java
public class ClockService {
    @GraphQLQuery
    public String now() {
        return Instant.now().toString();
    }
}
```

```java
var provider = GraphQLProvider.from("", GraphQLMetricsListener.NO_OP, new ClockService());
try (var server = GraphQLServer.builder(provider).port(8080).build()) {
    server.start();
    server.join();
}
```

Then query it:

```bash
curl 'http://localhost:8080/graphql?query={now}'
```

or open http://localhost:8080/graphiql.

## Try it: demo API

```bash
./gradlew runDemo
```

starts a demo API at http://localhost:8080 (GraphiQL UI at `/graphiql`). Things to try:

```graphql
query        { hello(name: "aqueduct") }
mutation     { addItem(value: "first") }
query        { items }
subscription { countTo(limit: 5) }
```

## Subscriptions

Return a reactive-streams `Publisher` from a method annotated with `@GraphQLSubscription`:

```java
public class PriceService {
    private final StateChangePublisher<Price> prices = new StateChangePublisher<>();

    @GraphQLSubscription
    public Publisher<Price> priceUpdates() {
        return prices;
    }

    public void onPrice(Price price) {
        prices.update(price); // pushed to all subscribers
    }
}
```

`ChangeDetectionPublisher<T extends Keyable>` polls a `Supplier<List<T>>` on an interval
and emits only the items that changed since the last poll.

Clients can subscribe over WebSocket at `/graphql` using either the
`graphql-transport-ws` or the legacy `graphql-ws` subprotocol (GraphiQL works out of the box).

## Metrics

Implement `GraphQLMetricsListener` and pass it to `GraphQLProvider.from(...)` to receive
callbacks for every operation invocation and subscription start/end:

```java
var provider = GraphQLProvider.from("MyApp_", myListener, services...);
```

## Schema export

Annotate each resolver service with `@GraphQLApi`, and the schema can be exported by classpath
scan — no central enumeration, and services are never instantiated (constructor dependencies
don't matter):

```java
@GraphQLApi
public class OrderService {
    public OrderService(Database db, Clock clock) { ... } // never called during export

    @GraphQLMutation(name = "placeOrder")
    public Order placeOrder(@GraphQLArgument(name = "symbol") String symbol) { ... }
}
```

```groovy
tasks.register('exportGraphQLSchema', JavaExec) {
    group = 'graphql'
    description = 'Exports the GraphQL schema as SDL'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'com.aquatic.graphql.schema.SchemaExport'
    args = ['--package', 'com.example.myapp', '--output', 'schema.graphql']
}
```

`--package` is repeatable (or comma-separated), `--prefix MyApp_` applies operation-name
prefixing, and omitting `--output` prints to stdout. Mutation-only schemas automatically get a
`_noop` placeholder query (GraphQL requires a non-empty query type). See `exportDemoSchema` in
this repo's build for a working example.

## Custom ObjectMapper

```java
GraphQLServer.builder(provider)
        .objectMapper(myConfiguredMapper)
        .build();
```

The default mapper (see `GraphQLJson.defaultMapper()`) registers `JavaTimeModule`,
ignores unknown properties, and accepts case-insensitive enums.

## Versioning & license

Semantic versioning via git tags (`vX.Y.Z`). Published to Maven Central and GitHub Packages as
`com.aquatic:aqueduct`.

[MIT](LICENSE)
