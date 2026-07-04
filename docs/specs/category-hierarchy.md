# Category Hierarchy — writes

**Status:** Proposed. Follow-up to `product-model-extension.md`, which exposed the
category **tree on reads** (`parent_id` + nested `sub_categories`) but left every
**write** flat. This spec makes the write side hierarchy-aware so the back-office
can create, re-parent, and safely delete subcategories.

## Why

`product-model-extension.md` shipped the read side: `GET /categories` returns the
top-level categories with their children nested, and each `CategoryDto` carries
`parent_id`. But the write side never caught up:

- `POST /categories` accepts only `{ label }` and always creates a **root** — the
  create body has no `parent_id`, so a subcategory can never be persisted.
- `PUT /categories` accepts a `parent_id` in the body but the mapper drops it, so a
  category can never be **re-parented** (or moved back to top-level).
- `DELETE /categories/{id}` deletes unconditionally. A category that still has child
  categories is protected by a database relationship, so the delete fails with an
  unhandled technical error (500) instead of a clear, predictable response.

This blocks the back-office "add / rename / delete subcategory" flows.

## Scope

Category **create**, **update (re-parent)**, and **delete** semantics. Read shape is
unchanged. No admin-role gate is introduced (none exists anywhere in the service yet —
writes require only an authenticated caller; called out as a known follow-up, not
addressed here).

## Create — `POST /categories`

The create body gains an **optional** `parent_id`.

| `parent_id` | Result |
|-------------|--------|
| omitted / null | Category is created as **top-level** (current behavior). |
| a valid existing category id | Category is created as a **child** of that category. |
| an id that does not exist | **400** `PARENT_CATEGORY_NOT_FOUND` — nothing is created. |

The existing duplicate-label rule is unchanged: a label already in use returns **400**
`CATEGORY_ALREADY_EXISTS`. `label` remains required and non-blank.

## Update — `PUT /categories`

The update body already carries `parent_id` (it was ignored). It is now honored:

| `parent_id` on update | Result |
|-----------------------|--------|
| omitted / null | Category becomes / stays **top-level**. |
| a valid existing category id (≠ the category's own id) | Category is **re-parented** under it. |
| the category's own id | **400** `INVALID_CATEGORY_PARENT` — a category cannot be its own parent. |
| an id that does not exist | **400** `PARENT_CATEGORY_NOT_FOUND`. |

`label` continues to update as before and stays required/non-blank. Deeper cycle
detection (making a category a child of one of its own descendants) is **out of scope**
for the two-level catalog; only the direct self-parent case is rejected.

## Delete — `DELETE /categories/{id}`

Delete becomes hierarchy-aware:

| Target state | Result |
|--------------|--------|
| category has **no** child categories | Deleted — **200** (current behavior). |
| category **has** child categories | **409** `CATEGORY_HAS_CHILDREN` — nothing is deleted. The children must be moved or removed first. |

Out of scope: a category that is still referenced by **products** remains protected by
the existing database relationship (pre-existing behavior, not changed here). This spec
adds only the child-category guard, which is the case the back-office subcategory flows
can hit.

## Error codes (functional)

New `FunctionalException` subtypes, surfaced by the existing `GlobalExceptionHandler`
as `{ type: "FUNCTIONAL", error_code, message }`:

| errorCode | HTTP | When |
|-----------|------|------|
| `PARENT_CATEGORY_NOT_FOUND` | 400 | create/update references a `parent_id` that does not exist |
| `INVALID_CATEGORY_PARENT` | 400 | update sets a category's parent to itself |
| `CATEGORY_HAS_CHILDREN` | 409 | delete targets a category that still has child categories |

## JSON

Unchanged conventions — snake_case, null omission, ISO-8601. The only wire additions
are `parent_id` on the **create** body (already present on read + update bodies).

## Out of scope

- Admin-role authorization (no role gate exists in the service yet).
- Product ↔ category re-filing on delete (products keep their existing DB protection).
- Multi-level cycle detection beyond direct self-parenting.
- Frontend wiring — tracked separately as Phase B of the Cloud Shop redesign.
