/**
 * Back-office analytics — deterministic, seeded port of
 * design_handoff_cloud_shop/back-office.jsx `buildAnalytics()`.
 *
 * The catalog is real (loaded from `/api`), but per-product *sales* figures are
 * still synthesised deterministically from the product list — the backend has no
 * sales/analytics endpoint yet. A follow-up swaps these for real `/api`
 * analytics once that exists.
 */
import { type Category, type Product } from '../data/catalog';

export interface MonthSale {
  month: string;
  value: number;
}

export interface ProductSale extends Product {
  units: number;
  revenue: number;
}

export interface CategoryRevenue {
  id: string;
  name: string;
  value: number;
  pct: number;
}

export interface Analytics {
  sales: MonthSale[];
  productSales: ProductSale[];
  catBreakdown: CategoryRevenue[];
  totalRevenue: number;
}

/** Lehmer LCG — same constants as the prototype so the numbers match exactly. */
const seedRand = (seed: number): (() => number) => {
  let x = seed;
  return () => {
    x = (x * 16807) % 2147483647;
    return x / 2147483647;
  };
};

const MONTHS = ['Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'];

export function buildAnalytics(products: Product[], categories: Category[]): Analytics {
  const r = seedRand(42);

  // 12 months of sales — a gentle seasonal wave with a deterministic jitter.
  const sales: MonthSale[] = MONTHS.map((month, i) => {
    const base = 8000 + Math.sin(i / 2) * 2200 + i * 350;
    return { month, value: Math.round(base + r() * 1500) };
  });

  // Units sold + revenue per product, ranked by units.
  const productSales: ProductSale[] = products
    .map((p) => {
      const units = Math.round(20 + r() * 180);
      return { ...p, units, revenue: units * p.price };
    })
    .sort((a, b) => b.units - a.units);

  // Aggregate revenue by category.
  const catSales: Record<string, number> = {};
  productSales.forEach((p) => {
    catSales[p.cat] = (catSales[p.cat] || 0) + p.revenue;
  });
  const totalCatRev = Object.values(catSales).reduce((a, b) => a + b, 0);
  const catBreakdown: CategoryRevenue[] = categories
    .map((c) => ({
      id: c.id,
      name: c.name,
      value: catSales[c.id] || 0,
      pct: ((catSales[c.id] || 0) / totalCatRev) * 100,
    }))
    .sort((a, b) => b.value - a.value);

  return {
    sales,
    productSales,
    catBreakdown,
    totalRevenue: productSales.reduce((s, p) => s + p.revenue, 0),
  };
}
