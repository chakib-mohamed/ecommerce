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

This spec serves the fields **we've chosen to make real**, drops one, and splits
ratings/reviews out into their own feature.

## Scope decisions

| Field | Decision |
|-------|----------|
| `stock` | **Real** — new inventory count on a product, admin-editable. |
| subcategories (`sub`) | **Real** — expose the existing category tree and let each product carry an explicit category + subcategory. |
| `colors` | **Dropped** — no real merchandising source; the faked-color UI is removed from the frontend. |
| `rating` / `reviews` | **Split out** — moved to their own feature, see `product-reviews.md`. Frontend keeps the current id-hash fallback until that ships. |
| `tone`, `badge` | Stay frontend-derived (presentation only). `badge` now keys off the real `stock`. |

## In scope: `stock`

Inventory count per product.

- `stock` (integer, ≥ 0) added to the product model, settable via the admin
  product form and returned on reads.
- Drives the storefront/back-office "Low stock" badge and low-stock alerts
  (currently computed from a fallback).

## In scope: subcategories

The category model **already** supports a tree (a category may have a parent and
child categories), and the seed catalog already files every product under a leaf
subcategory. Today the API only exposes a flat `id`/`label` per category and never
tells the client which subcategory a product belongs to, so the frontend sets
`sub = ''` and the sub-tree UI collapses.

**Decision: expose the tree + state the product's subcategory explicitly.**

- **Category** gains, on read: `parent_id` (the parent category, omitted for
  top-level categories) and `sub_categories` (the nested children). `GET /categories`
  returns the top-level categories with their children nested.
- **Product** gains `category_id` (top-level category) and `subcategory_id`
  (omitted when filed directly under a top-level category), on both read and write.
- **Read derivation:** a product is filed under its leaf category. When that leaf
  has a parent, `category_id = parent`, `subcategory_id = leaf`. When the leaf is
  itself top-level, `category_id = leaf`, `subcategory_id` is omitted.

`featured` already has a real source (featured listing); `blurb` maps to the
product description; product imagery maps to the product image. These need no change.

## Out of scope

- **Ratings & reviews** → own feature, `docs/specs/product-reviews.md`.
- No change to the JSON serialization conventions (snake_case, null-omission,
  ISO-8601) — new fields follow them.
- This does not block the redesign: the adapter already renders real products,
  categories, prices, descriptions and images today.

## Frontend follow-through (after the backend ships)

- `catalog-adapter.ts` — read `stock` from the API (drop its fallback); set `cat`
  from `category_id` and `sub` from `subcategory_id`; build category `subs` from
  `sub_categories`; **remove the faked `colors`**. `tone` stays derived;
  `rating`/`reviews` fallbacks stay until the reviews feature lands.
- Remove the colors UI (swatch row, product-detail picker) and drop `color` from
  the storefront cart line.
- Re-enable the subcategory UI paths (Browse sub-tree, AdminProducts sub-grouping,
  AdminProductForm subcategory requirement) — they already degrade gracefully for a
  flat catalog and light up once real `sub` values arrive.
