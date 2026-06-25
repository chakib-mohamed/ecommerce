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
 * Runtime catalog — products + categories loaded from the real `/api` and
 * adapted to the design model (see `lib/catalog-adapter.ts`). Storefront and
 * back-office screens read from here instead of a hardcoded mock.
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

export const loadCatalog = createAsyncThunk('catalog/load', async () => {
  const [catsRes, prodsRes, featRes] = await Promise.all([
    restApi.get('/categories'),
    restApi.get('/products'),
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
