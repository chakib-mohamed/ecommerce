# Product Reviews & Ratings — proposed

**Status:** Proposed (not yet implemented). Split out from
`product-model-extension.md` as its own feature.

## Why

The Cloud Shop frontend shows a per-product star **rating** (0–5) and a **review
count**. Neither has a backend source today — the adapter fakes both with a
deterministic id-hash fallback (`frontend/src/lib/catalog-adapter.ts`). Making
these real means letting customers leave reviews and aggregating them, which is a
feature in its own right rather than a couple of product columns.

## Goal

Customers can review products they've engaged with; each product exposes an
aggregate `rating` and `review_count` derived from real reviews.

## Sketch (to be refined when prioritized)

- **Review** record: product reference, author, star value (1–5), optional text,
  created timestamp.
- **Submit / list** endpoints for a product's reviews.
- **Aggregation:** `rating` (average stars) and `review_count` surfaced on the
  product so the storefront/back-office read them directly.

## Open questions (resolve in this feature's own session)

- **Who can review** — any authenticated user, or only users who have ordered the
  product?
- **One review per user per product?** Edit / delete own review?
- **Where aggregation lives** — recomputed in `products-service`, or owned by a
  dedicated reviews source and joined?
- **Moderation** — any need to hide/flag reviews?

## Workflow

Follows the standard repo gate sequence per `CLAUDE.md`: **spec → OpenAPI
(approved) → failing tests (approved) → implementation**. New fields/payloads obey
the JSON serialization conventions (snake_case, null-omission, ISO-8601).

## Until this ships

The frontend keeps its deterministic `rating`/`reviews` fallback so the UI stays
stable and consistent.
