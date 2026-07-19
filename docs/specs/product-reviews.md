# Product Reviews & Ratings

**Status:** Proposed — decisions resolved, OpenAPI contract not yet written. Split out from
`product-model-extension.md` as its own feature.

## Why

The Cloud Shop frontend shows a per-product star **rating** (0–5) and a **review count**. Neither
has a backend source today — the adapter fakes both with a deterministic id-hash fallback
(`frontend/src/lib/catalog-adapter.ts`). Making these real means letting customers leave reviews
and aggregating them, which is a feature in its own right rather than a couple of product columns.

## Goal

Customers who have purchased a product can review it; each product exposes an aggregate `rating`
and `review_count` derived from real reviews.

## Decisions

- **Who can review — verified purchasers only.** A user may review a product only if they have an
  order containing that product. This is checked at submission time against the buyer's order
  history (products-service calling orders-service — see Architecture below), mirroring the
  "Verified Purchase" pattern common in e-commerce and giving reviews real trust weight.
- **One review per (user, product), editable and deletable by its author.** Submitting again for a
  product the user already reviewed updates their existing review (upsert on `(product_id,
  reviewer)`) rather than creating a second one. The author can delete their own review at any
  time.
- **Aggregation lives in `products-service`.** Reviews are a table in `products-service`'s
  Postgres database, not a separate service. `Product.rating`/`Product.review_count` are
  maintained as columns updated in the same transaction as the write (create/update/delete of a
  review), so they're always consistent with the underlying review rows — no cross-service
  event propagation needed for this part. (This is a deliberate departure from the
  price-changed/Kafka pattern `products-service` already consumes from `price-service` — reviews
  and their aggregate live in the same service and the same transaction, so there's nothing to
  propagate.)
- **No moderation in this iteration.** Submit/list/aggregate ship first; hide/flag capability is
  deferred until real review volume exists to justify it.

## Architecture

- New `Review` entity in `products-service` (Postgres, Liquibase-migrated): `product_id` (FK),
  `reviewer` (buyer identity — matching how `orders-service` scopes orders by buyer email, see
  `frontend/src/containers/Orders/Orders.tsx`'s `buyerId`), `stars` (1–5), optional `text`,
  `created_at`. Unique constraint on `(product_id, reviewer)`.
- **Purchase verification**: at review submission, `products-service` needs to confirm the
  reviewer has a qualifying order — a synchronous REST Client call from `products-service` to
  `orders-service` (matching this repo's existing synchronous inter-service call conventions — no
  reactive stack outside the gateway, per `docs/conventions/architecture-conventions.md`). Exact
  request/response shape is settled in the OpenAPI step; likely reuses `orders-service`'s existing
  buyer-scoped order search rather than adding a new endpoint there.
- **`products-service` gains JWT verification.** It currently has none — no
  `quarkus-smallrye-jwt` dependency, no `mp.jwt.verify.publickey`, and no endpoint reads
  `SecurityContext`. Every write today is reachable by anyone who clears the gateway's
  `.anyExchange().authenticated()` check, with no further identity check inside the service. The
  review-submission endpoint needs to know *who* the reviewer is (for the `(product_id,
  reviewer)` upsert key and to pass to the purchase-verification call), so this feature adds
  JWT verification to `products-service`, following `orders-service`'s existing pattern
  (`mp.jwt.verify.publickey`, `@Context SecurityContext` → `getUserPrincipal().getName()` — see
  `OrdersResource.java`).
- **Aggregate maintenance**: `ReviewService` (control layer) recomputes and persists
  `Product.rating`/`Product.review_count` within the same `@Transactional` boundary as the review
  write, per this repo's persistence conventions (`docs/conventions/persistence-conventions.md`) —
  no network I/O inside that transaction, so the purchase-verification call happens before the
  transactional write starts, not inside it.

## Sketch

- **Submit review** — `POST` a review for a product; rejected (403/`FunctionalException`) if the
  reviewer has no qualifying order for that product. Upserts on `(product_id, reviewer)`.
- **List reviews** — paginated reviews for a product.
- **Delete own review** — author-only; recomputes the product's aggregate afterward.
- Product read endpoints (`GET /products`, `GET /products/{id}`) gain new `rating` and
  `review_count` fields — these don't exist on the wire today at all; the frontend's fallback is
  computed entirely client-side (`catalog-adapter.ts`), not sourced from any API field.

## Workflow

Follows the standard repo gate sequence per `CLAUDE.md`: spec (this document) → **OpenAPI (next
step — not yet written, needs approval before any code)** → failing tests (approved) →
implementation. New fields/payloads obey the JSON serialization conventions (snake_case,
null-omission, ISO-8601).

## Until this ships

The frontend keeps its deterministic `rating`/`reviews` fallback so the UI stays stable and
consistent.
