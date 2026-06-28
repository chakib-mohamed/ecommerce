/**
 * Cloud Shop catalog types + design constants.
 *
 * Runtime data flows through the `catalog` Redux slice, populated from the real
 * `/api` by `lib/catalog-adapter.ts`. The backend serves
 * `category_id`/`subcategory_id`/`stock` and the nested category tree for real;
 * only `tone`/`badge` are presentation-derived and `rating`/`reviews` keep a
 * deterministic fallback until the reviews subsystem ships (see
 * `docs/specs/product-reviews.md`). This module keeps the shared *types* and the
 * brand constants both paths rely on.
 */

export interface Subcategory {
  id: string;
  name: string;
}

export interface Category {
  id: string;
  name: string;
  /** placeholder-tile tone (1..6) */
  tone: number;
  subs: Subcategory[];
}

export interface Product {
  id: string;
  name: string;
  price: number;
  /** category id */
  cat: string;
  /** subcategory id ('' when the backend has no subcategories) */
  sub: string;
  rating: number;
  reviews: number;
  stock: number;
  /** placeholder-tile tone (1..6) */
  tone: number;
  blurb: string;
  /** real product image URL when available; falls back to a `tone` placeholder tile */
  image?: string;
  featured?: boolean;
  badge?: 'New' | 'Low stock';
}

export const shop = {
  name: 'Cloud Shop',
  tagline: 'Home & living, gently considered.',
};
