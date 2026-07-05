/**
 * Server-driven catalog reads. Screens that need the real catalog — the browse
 * grid, the admin product list, product detail, the cart, and search — go
 * through here instead of relying on a fully-loaded catalog in the store. Each
 * call fetches exactly the slice it needs (a page, a single product, or a search
 * hit) and adapts it to the design's `Product` model.
 */
import { restApi } from '../axios-instance';
import { Product } from '../data/catalog';
import { adaptProduct, RawProduct } from './catalog-adapter';

// Paginated/by-id reads don't carry the featured flag (it lives on the featured
// feed); an empty set means `featured: false`, which only affects a cosmetic
// badge and the (within-page) featured sort.
const NO_FEATURED = new Set<string>();

export interface ProductPageQuery {
  /** Top-level category id — includes products filed under its subcategories. */
  categoryId?: string;
  /** Subcategory id — narrows to that subcategory only. */
  subcategoryId?: string;
  /** Zero-based page index. */
  page: number;
  /** Page size. A returned page shorter than this means there are no more. */
  size: number;
}

const toProducts = (data: unknown): Product[] =>
  (Array.isArray(data) ? (data as RawProduct[]) : []).map((p) => adaptProduct(p, NO_FEATURED));

/** Fetch a page of products, optionally scoped to a category or subcategory. */
export const fetchProductPage = (q: ProductPageQuery): Promise<Product[]> => {
  const params: Record<string, string | number> = { page: q.page, size: q.size };
  if (q.categoryId) params.category_id = q.categoryId;
  if (q.subcategoryId) params.subcategory_id = q.subcategoryId;
  return restApi.get('/products', { params }).then((res) => toProducts(res.data));
};

/** Fetch a single product by id, or `null` when it no longer exists. */
export const fetchProductById = (id: string): Promise<Product | null> =>
  restApi
    .get(`/products/${id}`)
    .then((res) => (res.data ? adaptProduct(res.data as RawProduct, NO_FEATURED) : null))
    .catch(() => null);

/** Search products by title across the whole catalog (case-sensitive match). */
export const searchProductsByTitle = (term: string, size = 6): Promise<Product[]> =>
  restApi
    .post('/products/search', { title: { operator: 'LIKE', value: `%${term}%` } }, { params: { size } })
    .then((res) => toProducts(res.data));
