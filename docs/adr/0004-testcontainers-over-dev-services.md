# ADR-0004: Testcontainers over Quarkus Dev Services for test infrastructure

- **Status:** Accepted
- **Date:** 2026-05-29
- **Deciders:** CHAKIB Mohamed
- **Related:** `docs/conventions/testing-conventions.md`

## Context

The Quarkus services need real backing stores (MongoDB, PostgreSQL, Kafka, Redis, LocalStack) during
integration tests. Quarkus offers **Dev Services**, which auto-start throwaway containers when it
detects an empty/absent connection string — convenient, but implicit: what starts, with which image
and config, is inferred by the framework rather than declared by the test. The alternative is to
drive containers explicitly with **Testcontainers** via `QuarkusTestResourceLifecycleManager`.

## Decision drivers

- **Explicit, reproducible** test infrastructure — the image, version, and config visible in code,
  identical across services and CI.
- Avoid the implicit "empty connection string triggers a container" mechanism, which is easy to
  break accidentally and hard to reason about.
- A path to **sharing one container across modules** for suite speed.

## Decision

**Always use Testcontainers — never Quarkus Dev Services — for test infrastructure.**

1. Set `%test.quarkus.devservices.enabled=false` in every service's test
   `application.properties`; never rely on empty connection strings to trigger Dev Services.
2. Each service that needs a store provides a `*TestResource` implementing
   `QuarkusTestResourceLifecycleManager`, which starts the real container and injects the connection
   string; every `@QuarkusTest` touching a store is annotated with `@QuarkusTestResource(...)`.
3. **Kafka in tests** uses `KafkaTestResource` (a real broker, `confluentinc/cp-kafka`).
   `smallrye-reactive-messaging-in-memory` is **banned** — tests run against a real broker.
4. **Container reuse** is mandatory: each `*TestResource` holds the container in a `static` field,
   constructs it with `.withReuse(true)`, guards startup with `if (!isRunning) start()`, and leaves
   `stop()` empty. Construction config is kept identical across services so the reuse hash matches
   and one container is genuinely shared (~20% suite speedup, concentrated in the Kafka-heavy
   services). Reuse requires `testcontainers.reuse.enable=true` per machine/CI.

## Considered options

| Option | Decision | Why |
|---|---|---|
| Explicit Testcontainers `*TestResource` | **Chosen** | Declarative image/version/config; shareable; reproducible across services + CI |
| Quarkus Dev Services | Rejected | Implicit container selection; the empty-connection-string trigger is fragile and obscure |
| In-memory messaging (`*-in-memory`) for Kafka | Rejected | Doesn't exercise the real broker/serialization path; banned |

## Consequences

**Positive**
- Test infrastructure is explicit and identical across services; Docker-running is the only
  precondition.
- Real broker/store behavior is exercised (serialization, transactions, replica-set semantics).
- Container reuse keeps the suite fast.

**Negative / costs**
- Docker must be running for tests to pass.
- **Reused containers persist across runs and are not reset between runs** — every test must set up
  its own data and never assume an empty store. Stale containers are reclaimed manually
  (`docker rm -f $(docker ps -q --filter label=org.testcontainers=true)`).
