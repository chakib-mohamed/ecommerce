# Product Model Extension — proposed

**Status:** Proposed (not yet implemented). Tracked follow-up to the Cloud Shop
redesign's real-data wiring.

## Why

The Cloud Shop frontend redesign was built against a rich product model. When the
storefront and back-office were wired to the real `/api`, several fields turned
out to have no backend source. The frontend currently fills them with
**deterministic fallbacks** derived from the product id (see
`frontend/src/lib/catalog-adapter.ts`), so the UI is stable and consistent — but
the values are synthetic, not real merchandising data.

This spec proposes serving those fields for real so the adapter can prefer the
backend value and the fallback covers only what is genuinely absent.

## Gap

Fields the design model needs that the products API does **not** serve today:

| Field | Type | Today (fallback) | Proposed source |
|-------|------|------------------|-----------------|
| `sub` (subcategory) | string id | `''` → flat catalog | Subcategory under a category |
| `colors` | enum[] (swatches) | hashed from id (2–4) | Product variant colours |
| `stock` | int | hashed from id | Inventory count |
| `rating` | number (0–5) | hashed from id | Aggregated review score |
| `reviews` | int | hashed from id | Review count |
| `badge` | `New` \| `Low stock` | derived from stock/featured | Optional, or keep derived |

`featured` already has a real source (`GET /products/featured`); `blurb` maps to
`description`; product imagery maps to `image`. These need no change.

## Scope (when prioritized)

Follows the standard workflow gate per `CLAUDE.md`: **spec → OpenAPI (approved) →
failing tests (approved) → implementation**, across:

- `products-api` DTOs — add the new optional fields.
- `products-service` — persistence (PostgreSQL) + read/write mapping; introduce a
  subcategory concept under categories; surface inventory/stock.
- Reviews/ratings: decide whether to aggregate in `products-service` or expose a
  dedicated source; `rating`/`reviews` may warrant their own feature.
- Gateway — no routing change expected (same `/products`, `/categories` paths).

## Frontend follow-through (after the backend ships)

- `catalog-adapter.ts` — prefer the real field; keep the fallback only for any
  field still missing.
- `RawProduct` / `RawCategory` — extend with the new fields.
- Re-enable subcategory UI paths (Browse sub-tree, AdminProducts sub-grouping,
  AdminProductForm subcategory requirement) which currently degrade gracefully
  for a flat catalog.

## Non-goals

- No change to the JSON serialization conventions (snake_case, null-omission,
  ISO-8601) — new fields follow them.
- This does not block the redesign: the adapter already renders real products,
  categories, prices, descriptions and images today.
