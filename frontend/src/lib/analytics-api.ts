/**
 * Back-office analytics — aggregated sales figures for the admin dashboard. Requires the caller
 * to be signed in; the bearer token is attached by the axios interceptor.
 */
import { restApi } from '../axios-instance';
import type { Analytics, CategoryRevenue, MonthSale, ProductSale } from './analytics';

/** The wire shape, snake_case as every API in this platform. */
interface AnalyticsPayload {
  sales?: { month: string; value: number }[];
  product_sales?: { product_id: string; name?: string; units: number; revenue: number }[];
  category_breakdown?: { id: string; name?: string; value: number; pct: number }[];
  total_revenue?: number;
}

const toMonthSale = (m: { month: string; value: number }): MonthSale => ({
  month: m.month,
  value: m.value ?? 0,
});

const toProductSale = (p: {
  product_id: string;
  name?: string;
  units: number;
  revenue: number;
}): ProductSale => ({
  id: p.product_id,
  // A product sold before the catalog ever announced it has no name to show.
  name: p.name ?? 'Unknown product',
  units: p.units ?? 0,
  revenue: p.revenue ?? 0,
});

const toCategoryRevenue = (c: {
  id: string;
  name?: string;
  value: number;
  pct: number;
}): CategoryRevenue => ({
  id: c.id,
  name: c.name ?? 'Uncategorized',
  value: c.value ?? 0,
  pct: c.pct ?? 0,
});

export const adaptAnalytics = (payload: AnalyticsPayload): Analytics => ({
  sales: (payload.sales ?? []).map(toMonthSale),
  productSales: (payload.product_sales ?? []).map(toProductSale),
  catBreakdown: (payload.category_breakdown ?? []).map(toCategoryRevenue),
  totalRevenue: payload.total_revenue ?? 0,
});

export const fetchAnalytics = (): Promise<Analytics> =>
  restApi.get('/analytics').then((res) => adaptAnalytics(res.data ?? {}));
