/**
 * Cloud Shop catalog types + design constants.
 *
 * The redesign was built against a rich product model (colours, rating, stock,
 * subcategories, …). The real `/api` backend serves a leaner model, so the
 * runtime data now flows through the `catalog` Redux slice, populated from the
 * API by `lib/catalog-adapter.ts`. This module keeps the shared *types* and the
 * design palette/brand constants that both paths rely on.
 *
 * Backend model gap (filled with deterministic fallbacks by the adapter):
 * `sub`, `colors`, `rating`, `reviews`, `stock`, `tone`, `badge`. A future
 * backend extension (own spec/OpenAPI cycle) can serve these for real — see
 * `docs/specs/product-model-extension.md`.
 */

export type Swatch =
  | 'sand' | 'cream' | 'clay' | 'rust' | 'olive'
  | 'sage' | 'slate' | 'ink' | 'charcoal' | 'blush';

/** Hex values for the product-swatch palette (color dots). */
export const SWATCHES: Record<Swatch, string> = {
  sand: '#d8c9af',
  cream: '#efe7d6',
  clay: '#b07a5b',
  rust: '#a65a3a',
  olive: '#7d7f5a',
  sage: '#8a9479',
  slate: '#6f7b85',
  ink: '#2a2823',
  charcoal: '#43403a',
  blush: '#d6b3a3',
};

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
  colors: Swatch[];
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
