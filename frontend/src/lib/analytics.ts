/**
 * Shapes for the back-office analytics figures.
 *
 * These used to be synthesised in the browser from the product list, because no backend served
 * them. They now come from `/api/analytics`, which aggregates completed orders — see
 * `analytics-api.ts` for the call and `use-analytics.ts` for the hook the dashboard uses.
 */

export interface MonthSale {
  month: string;
  value: number;
}

export interface ProductSale {
  id: string;
  name: string;
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
