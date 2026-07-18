# Product Browse — filtering & pagination

**Status:** Proposed.

## Why

The storefront browse view and the back-office product list both need to page through the
catalog instead of loading it whole. Today the product list endpoint paginates
(`page`/`size`), but the storefront ignores it and pulls a single page, so once the catalog
grows past one page most products become invisible to browse, search-by-navigation, and the
admin list.

The browse view is **category-scoped** (`/browse/:cat`, `/browse/:cat/:sub`), and it currently
narrows the catalog to a category **on the client**. That only works while the whole catalog is
loaded. To page a category server-side, the list endpoint has to filter by category itself —
which it cannot do today (the only filtered endpoint matches scalar fields like title/price, not
a product's category).

This spec adds **category filtering to the product list** so the client can request exactly the
slice it shows, one page at a time.

## Scope

- Extend `GET /products` with two **optional** filters: `category_id` and `subcategory_id`.
- Keep the existing `page`/`size` pagination unchanged.
- **Load-more** semantics: the response stays a plain array. There is **no total count** — the
  client keeps requesting the next page and stops when a page returns fewer than `size` items.

### Out of scope

- Numbered pages / a total-count contract. (Deliberately avoided — load-more needs none.)
- Server-side sorting. Sort order within the loaded set stays a client concern.
- Any change to how products are categorized, or to the create/update/delete contracts.

## Filtering behavior

A product is filed under a single **leaf** category. When that leaf sits under a parent, the
parent is the product's top-level category and the leaf is its subcategory; when the leaf is
itself top-level, the product has no subcategory.

`GET /products` gains:

| Parameters | Returns |
|------------|---------|
| none | All products (current behavior). |
| `category_id=C` | Products in category **C** — both those filed directly under C and those in any of C's subcategories. |
| `subcategory_id=S` | Products filed under subcategory **S**. |
| `category_id=C` & `subcategory_id=S` | Products filed under subcategory **S** (the more specific filter wins). |

- Both filters are optional and combine with `page`/`size`.
- An id that matches no category yields an **empty page**, not an error — filtering by a missing
  value is a valid, empty result.

## Pagination behavior

- `page` (0-based) and `size` keep their current defaults.
- The response is a JSON array of the matching page.
- A returned page **shorter than `size`** (including empty) signals the last page. Clients use
  this to decide whether to offer "load more".

## Backward compatibility

`category_id` and `subcategory_id` are optional; callers that pass neither see no change. The
response shape is unchanged.

## Consumers

- Storefront **Browse** — loads the first page for the current category/subcategory and appends
  further pages on "Load more".
- Back-office **product list** — pages the catalog the same way.
- **Product detail** and **cart hydration** stop relying on a fully-loaded catalog and fetch a
  product by id (`GET /products/{id}`, already available) instead.
