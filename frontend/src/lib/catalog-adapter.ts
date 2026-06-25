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

/** Raw category entry: GET /categories returns `{ <id>: <name> }`. */
export interface RawCategory {
  id: string;
  name: string;
}

/** Raw product as served by GET /products (loose — fields vary). */
export interface RawProduct {
  id?: string;
  productID?: string;
  name?: string;
  title?: string;
  description?: string;
  image?: string;
  price?: number | string;
  stock?: number;
  category?: string | { id?: string; value?: string; label?: string } | null;
}

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

const categoryId = (category: RawProduct['category']): string => {
  if (!category) return 'general';
  if (typeof category === 'string') return category;
  return String(category.id ?? category.value ?? 'general');
};

export const adaptCategory = (raw: RawCategory, index: number): Category => ({
  id: String(raw.id),
  name: raw.name,
  tone: (index % 6) + 1,
  subs: [], // backend has no subcategories yet
});

export const adaptProduct = (raw: RawProduct, featuredIds: Set<string>): Product => {
  const id = String(raw.productID ?? raw.id ?? '');
  const r = seeded(hashId(id || 'x'));
  const colors = pickColors(r);
  const rating = Math.round((3.8 + r() * 1.2) * 10) / 10;
  const reviews = 5 + Math.floor(r() * 120);
  const tone = Math.floor(r() * 6) + 1;
  const stock = typeof raw.stock === 'number' ? raw.stock : 3 + Math.floor(r() * 40);
  const featured = featuredIds.has(id);

  return {
    id,
    name: raw.name ?? raw.title ?? 'Untitled',
    price: Number(raw.price ?? 0),
    cat: categoryId(raw.category),
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
