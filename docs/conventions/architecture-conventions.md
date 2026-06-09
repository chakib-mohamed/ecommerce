# Architecture Conventions

Applies to all Quarkus services.

## Package Conventions

Each service follows a four-package BCE layout under its root package, with **`repository` as a top-level sibling** of `boundary`, `control`, and `entity` — never nested inside `control`:

```
the.chak.ecommerce.<service>/
├── boundary     # JAX-RS resources (@Path) + boundary/dto
├── control      # services, control/events (Kafka payloads), exceptions
├── entity       # @Entity / @MongoEntity persistence classes
└── repository   # Panache repositories
```

All DTOs (request/response objects, value objects, commands) live in `boundary/dto/` within each service or shared-api module. The `entity/` package is reserved exclusively for persistence-annotated domain objects (Panache entities and their embedded value objects).

Kafka event payloads live in `control/events/` — they are messaging objects, not HTTP DTOs, so they stay in the control layer.

These placements are enforced by `BceArchitectureTest` in each service: `@Path` resources must reside in `boundary`, persistence entities in `entity`, `*Exception` classes in `control`, and any `PanacheRepositoryBase`/`PanacheMongoRepositoryBase` implementation in `repository`.

## Reactive Stack Policy

The reactive stack is **only allowed in `ecommerce-api-gateway`** (Spring WebFlux / Project Reactor). All six Quarkus services must use the blocking, imperative stack exclusively.

**Banned in every Quarkus service** — do not add these to any `pom.xml` or import them in any `.java` file:

| What | Examples |
|------|---------|
| Reactive REST layer | `quarkus-resteasy-reactive`, `quarkus-rest` |
| Reactive Panache (ORM) | `quarkus-hibernate-reactive-panache` |
| Reactive Panache (MongoDB) | `quarkus-mongodb-panache-reactive` |
| Reactive routes | `quarkus-reactive-routes` |
| Mutiny library | `quarkus-mutiny`, `io.smallrye.reactive:smallrye-mutiny` |
| In-memory messaging connector | `io.smallrye.reactive:smallrye-reactive-messaging-in-memory` |
| Reactive types in code | `Uni<T>`, `Multi<T>` (Mutiny), `Mono<T>`, `Flux<T>` (Reactor) |

**Allowed** — these are messaging infrastructure, not user-facing reactive APIs:
- `quarkus-messaging-kafka` — Kafka producer/consumer channels

Use blocking JAX-RS (`quarkus-resteasy`) and synchronous Panache for all HTTP endpoints and database access.

## Kafka Serialization

All Kafka producers use `io.quarkus.kafka.client.serialization.JsonbSerializer` (Jakarta JSON-B), not Jackson. Don't switch to `JsonSerializer` from the Kafka library.

## Lombok Rules

**`@Data` is banned on entity classes.** It generates `equals`/`hashCode`/`toString`/setters indiscriminately, which breaks entity identity. On plain DTOs (control-layer objects, boundary DTOs) `@Data` is permitted.

### By class type:

| Class type | Allowed annotations | Rationale |
|------------|-------------------|-----------|
| JPA entity (`PanacheEntity`) | `@Getter` + `@Setter` | No `equals`/`hashCode` override — use JPA identity. Use `@Getter(AccessLevel.NONE)` on fields with manual getter overrides (e.g. lazy-init collections). |
| Panache MongoDB entity (`PanacheMongoEntity`) | `@Getter` + `@Setter` | Same convention as JPA entities — private fields with accessors. |
| CDI/Spring config bean | `@Getter` only | Config must be immutable after injection — never expose setters. |
| Plain DTO (control-layer, boundary) | `@Data` permitted | No entity identity concerns; `@Data` is acceptable shorthand. |
| Kafka event payload | `@Getter` + `@Setter` + `@NoArgsConstructor` + `@AllArgsConstructor` | JSON-B needs no-arg constructor for deserialization; all-args for construction in producers. |
| Immutable value object | `@Getter` + `@AllArgsConstructor` (with `final` fields) | True value semantics — no setters. |

### Banned annotations:

| Annotation | Why |
|------------|-----|
| `@Data` on entity classes | Generates `equals`/`hashCode` on mutable fields, breaks entity identity, `toString` can trigger lazy-loading |
| `@EqualsAndHashCode` on entities | Entity identity must come from the database ID, not field-based hashing |
| `@ToString` on entities | Can trigger lazy-loading of relationships and leak sensitive data |

### Additional rules:

- **Do not mix public fields with `@Getter`/`@Setter`** — pick one convention. Panache Mongo = public fields (no Lombok); everything else = private fields + Lombok accessors.
- **When overriding a generated getter** (e.g. null-safe collection init), annotate the field with `@Getter(AccessLevel.NONE)` to signal intent and avoid dead-code confusion.
- **Never use `@Setter` on config/security-sensitive fields** (keys, secrets, tokens).
