/**
 * Adapts the real `/api` product/category payloads to the design's catalog
 * model. The backend now serves `category_id`/`subcategory_id`/`stock` on
 * products and the nested category tree (`parent_id` + `sub_categories`), so
 * those flow through as real data. The remaining presentation-only fields
 * (`tone`, `badge`) are derived, and `rating`/`reviews` keep *deterministic*
 * id-seeded fallbacks until the reviews subsystem ships (see
 * `docs/specs/product-reviews.md`).
 */
import { Category, Product } from '../data/catalog';

/**
 * Raw category entry. GET /categories returns the top-level categories, each
 * with its children nested under `sub_categories` and a `parent_id` link — see
 * `adaptCategory`.
 */
export interface RawCategory {
  id?: number | string;
  label?: string;
  name?: string;
  parent_id?: number | string;
  sub_categories?: RawCategory[];
}

type RawCategoryRef = string | { id?: number | string; label?: string; value?: string };

/** Raw product as served by GET /products (loose — field names vary by source). */
export interface RawProduct {
  uuid?: string;
  id?: string;
  productID?: string;
  product_id?: string;
  name?: string;
  title?: string;
  description?: string;
  image?: string;
  price?: number | string;
  stock?: number;
  category_id?: number | string;
  subcategory_id?: number | string;
  category?: RawCategoryRef | null;
  categories?: RawCategoryRef[] | null;
}

/**
 * The product's canonical id. `/products` keys on `uuid`; other shapes may use
 * `productID`/`product_id`/`id` — prefer whichever is present, consistently.
 */
export const productId = (raw: RawProduct): string =>
  String(raw.uuid ?? raw.productID ?? raw.product_id ?? raw.id ?? '');

/**
 * The value that joins a featured entry back to a product. `/products/featured`
 * carries `product_id` (the product's `uuid`) alongside its own document `id`,
 * so the join key is `product_id` first.
 */
export const featuredKey = (raw: RawProduct): string =>
  String(raw.product_id ?? raw.uuid ?? raw.productID ?? raw.id ?? '');

/** FNV-1a hash of a string → a stable positive 31-bit seed. */
const hashId = (s: string): number => {
  let h = 2166136261;
  for (let i = 0; i < s.length; i += 1) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return ((h >>> 0) % 2147483646) + 1;
};

/** Lehmer LCG seeded from the id — same family as the analytics seed. */
const seeded = (seed: number): (() => number) => {
  let x = seed;
  return () => {
    x = (x * 16807) % 2147483647;
    return x / 2147483647;
  };
};

const refId = (ref: RawCategoryRef | null | undefined): string | null => {
  if (ref == null) return null;
  if (typeof ref === 'string') return ref;
  return String(ref.id ?? ref.value ?? ref.label ?? '') || null;
};

/**
 * A product's top-level category id. Prefers the explicit `category_id`; falls
 * back to the embedded `categories[0]`/`category` reference for older shapes.
 */
const categoryId = (raw: RawProduct): string => {
  if (raw.category_id != null) return String(raw.category_id);
  const first = Array.isArray(raw.categories) ? raw.categories[0] : undefined;
  return refId(first) ?? refId(raw.category) ?? 'general';
};

/** A product's subcategory id, or `''` when it's filed under a top-level category. */
const subcategoryId = (raw: RawProduct): string =>
  raw.subcategory_id != null ? String(raw.subcategory_id) : '';

export const adaptCategory = (raw: RawCategory, index: number): Category => ({
  id: String(raw.id ?? ''),
  name: raw.label ?? raw.name ?? 'Uncategorized',
  tone: (index % 6) + 1,
  subs: Array.isArray(raw.sub_categories)
    ? raw.sub_categories.map((s) => ({
        id: String(s.id ?? ''),
        name: s.label ?? s.name ?? 'Uncategorized',
      }))
    : [],
});

export const adaptProduct = (raw: RawProduct, featuredIds: Set<string>): Product => {
  const id = productId(raw);
  const r = seeded(hashId(id || 'x'));
  const rating = Math.round((3.8 + r() * 1.2) * 10) / 10;
  const reviews = 5 + Math.floor(r() * 120);
  const tone = Math.floor(r() * 6) + 1;
  const stock = Number(raw.stock ?? 0);
  const featured = featuredIds.has(id);

  return {
    id,
    name: raw.title ?? raw.name ?? 'Untitled',
    price: Number(raw.price ?? 0),
    cat: categoryId(raw),
    sub: subcategoryId(raw),
    rating,
    reviews,
    stock,
    tone,
    blurb: raw.description ?? '',
    image: raw.image || undefined,
    featured,
    badge: stock <= 5 ? 'Low stock' : featured ? 'New' : undefined,
  };
};
