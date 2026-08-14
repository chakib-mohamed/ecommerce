# Persistence Conventions

Applies to all Quarkus services.

## Panache Patterns

**Panache ORM** (`products-service`): Entity methods are static — use `Product.findById(id)`, not instance calls. The active record pattern means queries live on the entity class.

**Panache MongoDB** (`authenticate-service`, `featured-products-service`, `orders-service`, `price-service`): Same active-record style but for MongoDB documents.

## Database Transaction Rules

### Transaction boundary

`@Transactional` belongs on control-layer (service) methods that mutate state — **never on resource (REST endpoint) classes**. This mirrors the exception-handling pattern: resources orchestrate, control layers own the work.

### No I/O inside a transaction

Never perform HTTP calls, Kafka produces, or any external network I/O inside a `@Transactional` method. For services that write to both a database and Kafka (e.g. `products-service`), use the transactional outbox pattern: persist the event record inside the transaction, publish to Kafka only after the commit succeeds.

### PostgreSQL (`products-service`)

`jakarta.transaction.Transactional` (JTA) has no `readOnly` attribute — that is Spring-only. For truly read-only methods that do not need session continuity across multiple queries, omit `@Transactional` entirely; Panache manages single-query reads automatically. Only add `@Transactional` when the method must keep the Hibernate session open across two or more queries (e.g. multi-bag join workarounds).

### MongoDB (all other Quarkus services)

Single-document operations do not need `@Transactional` — MongoDB's document model provides atomic single-document writes natively. Only add `@Transactional` when you need multi-document atomicity, and only when the MongoDB deployment supports sessions (replica set required). Do not add it by default.

### Rollback

Let unchecked exceptions propagate to trigger automatic rollback. Never catch-and-ignore inside a `@Transactional` method. Consistent with the two-tier `FunctionalException` / `GlobalExceptionHandler` model — functional errors should still propagate out and trigger rollback before the global handler maps them to a response.

### Keep transactions short

Validate inputs and authorise the request *before* opening a transaction. Do not include slow computations, bulk reads for display, or anything that can be done outside the write path.

## JPA / Entity Rules

All JPA relationship fields (`@ManyToOne`, `@OneToOne`, `@OneToMany`, `@ManyToMany`) **must** declare `fetch = FetchType.LAZY` explicitly. `@ManyToOne` and `@OneToOne` default to `EAGER`, which causes silent N+1 queries. Enforce with an ArchUnit test where possible so it becomes a CI gate.

## Indexing a sorted query

**Any query that sorts must have an index that covers the sort.** In MongoDB this is a correctness
rule, not a performance one: an in-memory sort is aborted once it exceeds 32MB, so an unindexed sort
does not get slower as a collection grows — it starts failing outright, and only in the environment
large enough to cross the threshold.

The same applies to a query the *sweeps* run. A background job scanning on an unindexed predicate
looks harmless while a collection is small and becomes a full scan on every tick once it is not.

`orders-service` creates its indexes at startup in `OrderIndexInitializer` rather than by hand, so a
fresh environment cannot come up without them:

| Index | Serves |
|---|---|
| `order_user_recent` — `userID`, `creationDate` desc | order history, which pages newest-first |
| `order_user_product_status` | the purchase check products-service makes before accepting a review |
| `order_status_created` — `status`, `creationDate` | the uncommitted-order expiry sweep |
| `order_step_deadline` — sparse | the saga deadline sweep, which only ever looks at orders that have one |

The last one is sparse deliberately: only orders with an outstanding step carry a deadline, and a
sparse index keeps the sweep's scan proportional to that set rather than to the collection.
