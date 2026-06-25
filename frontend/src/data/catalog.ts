/**
 * Cloud Shop mock catalog — typed port of design_handoff_cloud_shop/catalog.js.
 *
 * This is the temporary data layer for the redesign: screens read from CATALOG
 * so the visuals can be nailed against stable data. A follow-up swaps these reads
 * for the real `/api` calls in services/rest-api-service.ts.
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
  /** subcategory id */
  sub: string;
  rating: number;
  reviews: number;
  stock: number;
  /** placeholder-tile tone (1..6) */
  tone: number;
  colors: Swatch[];
  blurb: string;
  featured?: boolean;
  badge?: 'New' | 'Low stock';
}

export const categories: Category[] = [
  {
    id: 'home-living', name: 'Home & Living', tone: 1, subs: [
      { id: 'furniture', name: 'Furniture' },
      { id: 'lighting', name: 'Lighting' },
      { id: 'decor', name: 'Decor' },
    ],
  },
  {
    id: 'kitchen', name: 'Kitchen', tone: 4, subs: [
      { id: 'cookware', name: 'Cookware' },
      { id: 'tableware', name: 'Tableware' },
      { id: 'storage', name: 'Storage' },
    ],
  },
  {
    id: 'apparel', name: 'Apparel', tone: 2, subs: [
      { id: 'knitwear', name: 'Knitwear' },
      { id: 'tops', name: 'Tops' },
      { id: 'accessories', name: 'Accessories' },
    ],
  },
  {
    id: 'outdoors', name: 'Outdoors', tone: 6, subs: [
      { id: 'garden', name: 'Garden' },
      { id: 'picnic', name: 'Picnic' },
    ],
  },
];

/**
 * Product seed — the fields explicitly authored in the handoff. Defaults for
 * rating/reviews/stock/tone/colors are derived deterministically by `build()`
 * below, matching the prototype's P() helper so values are identical.
 */
type ProductSeed = Partial<Product> &
  Pick<Product, 'name' | 'price' | 'cat' | 'sub' | 'blurb'>;

let n = 0;
const build = (o: ProductSeed): Product => {
  n += 1;
  return {
    id: 'p' + n.toString().padStart(2, '0'),
    rating: o.rating ?? 4 + (n % 2 ? 0.6 : 0.3),
    reviews: o.reviews ?? 12 + (n * 7) % 80,
    stock: o.stock == null ? 8 + (n * 5) % 30 : o.stock,
    tone: o.tone ?? ((n % 6) + 1),
    colors: o.colors ?? ['sand', 'slate', 'olive'],
    ...o,
  } as Product;
};

export const products: Product[] = [
  build({ name: 'Linen Table Lamp', price: 89, cat: 'home-living', sub: 'lighting', featured: true, badge: 'New', colors: ['sand', 'slate', 'olive'], blurb: 'A softly diffused linen shade on a turned-oak base. Warm, even light for a bedside or console.' }),
  build({ name: 'Oak Reading Chair', price: 420, cat: 'home-living', sub: 'furniture', featured: true, colors: ['clay', 'charcoal', 'sage'], blurb: 'Solid white-oak frame with a gently scooped seat. Built to be sat in for hours.' }),
  build({ name: 'Stoneware Vase', price: 48, cat: 'home-living', sub: 'decor', featured: true, colors: ['cream', 'clay', 'slate'], blurb: 'Hand-thrown stoneware with a matte reactive glaze. No two are exactly alike.' }),
  build({ name: 'Wool Throw Blanket', price: 96, cat: 'home-living', sub: 'decor', badge: 'Low stock', stock: 3, colors: ['sand', 'rust', 'sage'], blurb: 'Lambswool woven on a vintage loom. Heavy enough for winter, soft enough year-round.' }),
  build({ name: 'Paper Pendant Light', price: 64, cat: 'home-living', sub: 'lighting', colors: ['cream', 'sand'], blurb: 'A sculptural rice-paper pendant that glows like a paper moon.' }),
  build({ name: 'Low Slung Shelf', price: 240, cat: 'home-living', sub: 'furniture', colors: ['charcoal', 'sand'], blurb: 'A long, low ash shelf for books, records and the occasional plant.' }),

  build({ name: 'Cast Iron Skillet', price: 72, cat: 'kitchen', sub: 'cookware', featured: true, colors: ['ink', 'charcoal'], blurb: 'Pre-seasoned, pour-spouted and built to outlive you. From hob to oven to table.' }),
  build({ name: 'Speckled Dinner Set', price: 120, cat: 'kitchen', sub: 'tableware', badge: 'New', colors: ['cream', 'sage', 'slate'], blurb: 'Four plates, four bowls. Reactive speckle glaze, dishwasher-friendly.' }),
  build({ name: 'Glass Storage Jars', price: 38, cat: 'kitchen', sub: 'storage', colors: ['cream'], blurb: 'Set of three borosilicate jars with cork lids. For grains, pasta and good intentions.' }),
  build({ name: 'Walnut Cutting Board', price: 56, cat: 'kitchen', sub: 'cookware', colors: ['clay', 'charcoal'], blurb: 'End-grain walnut, oiled by hand. Kind to your knives.' }),
  build({ name: 'Stacking Mugs', price: 44, cat: 'kitchen', sub: 'tableware', colors: ['clay', 'sage', 'slate', 'cream'], blurb: 'Four mugs that nest neatly. The handle actually fits your fingers.' }),

  build({ name: 'Lambswool Crewneck', price: 110, cat: 'apparel', sub: 'knitwear', featured: true, colors: ['sand', 'rust', 'slate', 'olive'], blurb: 'A boxy, mid-weight crewneck in fully traceable lambswool.' }),
  build({ name: 'Garment-Dyed Tee', price: 42, cat: 'apparel', sub: 'tops', colors: ['cream', 'clay', 'sage', 'ink'], blurb: 'Heavyweight cotton, garment-dyed for that lived-in tone from day one.' }),
  build({ name: 'Felted Wool Hat', price: 58, cat: 'apparel', sub: 'accessories', colors: ['charcoal', 'sand', 'olive'], blurb: 'A packable felted-wool hat with a structured brim.' }),
  build({ name: 'Canvas Tote', price: 36, cat: 'apparel', sub: 'accessories', badge: 'New', colors: ['cream', 'olive'], blurb: 'Heavy 16oz canvas, boxed base, straps that sit on the shoulder.' }),
  build({ name: 'Ribbed Beanie', price: 32, cat: 'apparel', sub: 'knitwear', colors: ['rust', 'slate', 'charcoal', 'cream'], blurb: 'A close-knit merino beanie that holds its shape.' }),

  build({ name: 'Enamel Picnic Set', price: 68, cat: 'outdoors', sub: 'picnic', colors: ['cream', 'sage'], blurb: 'Chip-resistant enamel plates and cups for four. Camp-proof, charming.' }),
  build({ name: 'Hand Trowel', price: 28, cat: 'outdoors', sub: 'garden', colors: ['charcoal'], blurb: 'Forged carbon steel with an ash handle. Balanced and properly sharp.' }),
  build({ name: 'Folding Stool', price: 84, cat: 'outdoors', sub: 'picnic', colors: ['olive', 'sand'], blurb: 'A beech-and-canvas folding stool that earns its place in the boot.' }),
  build({ name: 'Watering Can', price: 52, cat: 'outdoors', sub: 'garden', colors: ['sage', 'clay'], blurb: 'Galvanised steel with a long spout and a fine brass rose.' }),
];

export const shop = {
  name: 'Cloud Shop',
  tagline: 'Home & living, gently considered.',
};

export const CATALOG = { shop, categories, products };
