/**
 * Adapts the real `/api` product/category payloads to the design's catalog
 * model. Fields the backend doesn't serve (sub, colors, rating, reviews, stock,
 * tone, badge) are filled with *deterministic* fallbacks derived from the
 * product id, so a given product always looks the same across renders/sessions.
 *
 * When the backend grows these fields for real (see
 * `docs/specs/product-model-extension.md`), prefer the real value and let the
 * fallback cover only what's still missing.
 */
import { Category, Product, Swatch, SWATCHES } from '../data/catalog';

const SWATCH_KEYS = Object.keys(SWATCHES) as Swatch[];

/**
 * Raw category entry. GET /categories returns a flat array of `{ id, label }`
 * (top-level categories and subcategories share the list, with no parent link),
 * so the adapter keeps them flat — see `adaptCategory`.
 */
export interface RawCategory {
  id?: number | string;
  label?: string;
  name?: string;
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

const pickColors = (r: () => number): Swatch[] => {
  const pool = [...SWATCH_KEYS];
  const count = 2 + Math.floor(r() * 3); // 2..4 colours
  const out: Swatch[] = [];
  for (let i = 0; i < count && pool.length; i += 1) {
    out.push(pool.splice(Math.floor(r() * pool.length), 1)[0]);
  }
  return out;
};

const refId = (ref: RawCategoryRef | null | undefined): string | null => {
  if (ref == null) return null;
  if (typeof ref === 'string') return ref;
  return String(ref.id ?? ref.value ?? ref.label ?? '') || null;
};

/** A product's category id — `categories[0]` (array form) or `category`. */
const categoryId = (raw: RawProduct): string => {
  const first = Array.isArray(raw.categories) ? raw.categories[0] : undefined;
  return refId(first) ?? refId(raw.category) ?? 'general';
};

export const adaptCategory = (raw: RawCategory, index: number): Category => ({
  id: String(raw.id ?? ''),
  name: raw.label ?? raw.name ?? 'Uncategorized',
  tone: (index % 6) + 1,
  subs: [], // backend serves a flat list with no parent link yet
});

export const adaptProduct = (raw: RawProduct, featuredIds: Set<string>): Product => {
  const id = productId(raw);
  const r = seeded(hashId(id || 'x'));
  const colors = pickColors(r);
  const rating = Math.round((3.8 + r() * 1.2) * 10) / 10;
  const reviews = 5 + Math.floor(r() * 120);
  const tone = Math.floor(r() * 6) + 1;
  const stock = typeof raw.stock === 'number' ? raw.stock : 3 + Math.floor(r() * 40);
  const featured = featuredIds.has(id);

  return {
    id,
    name: raw.title ?? raw.name ?? 'Untitled',
    price: Number(raw.price ?? 0),
    cat: categoryId(raw),
    sub: '',
    rating,
    reviews,
    stock,
    tone,
    colors: colors.length ? colors : ['sand'],
    blurb: raw.description ?? '',
    image: raw.image || undefined,
    featured,
    badge: stock <= 5 ? 'Low stock' : featured ? 'New' : undefined,
  };
};
