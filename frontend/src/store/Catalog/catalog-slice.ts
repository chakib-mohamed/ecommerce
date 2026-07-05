import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { restApi } from '../../axios-instance';
import { Category, Product } from '../../data/catalog';
import {
  adaptCategory,
  adaptProduct,
  featuredKey,
  RawCategory,
  RawProduct,
} from '../../lib/catalog-adapter';

/**
 * Runtime catalog held in the store: the full category tree (used everywhere for
 * navigation, filters and name lookups) plus a small **overview sample** of
 * products for the homepage feed and the admin dashboard. Screens that need the
 * real catalog — browse, the admin product list, product detail, cart and
 * search — read the server directly (paginated / by-id / server-search) rather
 * than this sample; see `lib/catalog-api.ts`.
 */
interface CatalogState {
  products: Product[];
  categories: Category[];
  status: 'idle' | 'loading' | 'ready' | 'error';
}

const initialState: CatalogState = {
  products: [],
  categories: [],
  status: 'idle',
};

// Products loaded into the store are only the homepage feed / dashboard overview
// sample — not a full catalog load. Browse and the admin list paginate the
// server per page instead.
const OVERVIEW_SAMPLE_SIZE = 24;

export const loadCatalog = createAsyncThunk('catalog/load', async () => {
  const [catsRes, prodsRes, featRes] = await Promise.all([
    restApi.get('/categories'),
    restApi.get('/products', { params: { size: OVERVIEW_SAMPLE_SIZE } }),
    // Featured is best-effort — an empty list just means nothing is flagged.
    restApi.get('/products/featured').catch(() => ({ data: [] as RawProduct[] })),
  ]);

  const catList = (Array.isArray(catsRes.data) ? catsRes.data : []) as RawCategory[];
  const categories = catList.map((c, i) => adaptCategory(c, i));

  const featList = (Array.isArray(featRes.data) ? featRes.data : []) as RawProduct[];
  const featuredIds = new Set(featList.map(featuredKey));

  const prodList = (Array.isArray(prodsRes.data) ? prodsRes.data : []) as RawProduct[];
  const products = prodList.map((p) => adaptProduct(p, featuredIds));

  return { categories, products };
});

const catalogSlice = createSlice({
  name: 'catalog',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(loadCatalog.pending, (state) => {
        state.status = 'loading';
      })
      .addCase(loadCatalog.fulfilled, (state, action) => {
        state.products = action.payload.products;
        state.categories = action.payload.categories;
        state.status = 'ready';
      })
      .addCase(loadCatalog.rejected, (state) => {
        state.status = 'error';
      });
  },
});

export default catalogSlice.reducer;
