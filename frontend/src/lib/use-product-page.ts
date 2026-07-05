import { useCallback, useEffect, useState } from 'react';
import type { Product } from '../data/catalog';
import { fetchProductPage } from './catalog-api';

const DEFAULT_PAGE_SIZE = 12;

export interface ProductPageState {
  /** Products accumulated so far (first page plus any "load more" appends). */
  items: Product[];
  /** True while a page request is in flight. */
  loading: boolean;
  /** True while the very first page for the current filter is loading. */
  initialLoading: boolean;
  /** Whether another page is available to load. */
  hasMore: boolean;
  /** True when the last request failed. */
  error: boolean;
  /** Append the next page. No-op while loading or when nothing more remains. */
  loadMore: () => void;
  /** Discard and re-fetch from the first page (e.g. after a mutation). */
  reload: () => void;
}

/**
 * Server-driven, load-more pagination for the product list, optionally scoped to
 * a category/subcategory. Resets to the first page whenever the filter changes.
 * A page shorter than `pageSize` marks the end (no total count needed).
 */
export function useProductPage(
  categoryId?: string,
  subcategoryId?: string,
  pageSize: number = DEFAULT_PAGE_SIZE,
): ProductPageState {
  const [items, setItems] = useState<Product[]>([]);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [initialLoading, setInitialLoading] = useState(true);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState(false);
  const [version, setVersion] = useState(0);

  const reload = useCallback(() => setVersion((v) => v + 1), []);

  // First page: (re)load whenever the filter — or an explicit reload — changes.
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setInitialLoading(true);
    setError(false);
    fetchProductPage({ categoryId, subcategoryId, page: 0, size: pageSize })
      .then((batch) => {
        if (cancelled) return;
        setItems(batch);
        setPage(0);
        setHasMore(batch.length === pageSize);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (cancelled) return;
        setLoading(false);
        setInitialLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [categoryId, subcategoryId, pageSize, version]);

  const loadMore = useCallback(() => {
    if (loading || !hasMore) return;
    const next = page + 1;
    setLoading(true);
    setError(false);
    fetchProductPage({ categoryId, subcategoryId, page: next, size: pageSize })
      .then((batch) => {
        setItems((cur) => [...cur, ...batch]);
        setPage(next);
        setHasMore(batch.length === pageSize);
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [categoryId, subcategoryId, page, pageSize, loading, hasMore]);

  return { items, loading, initialLoading, hasMore, error, loadMore, reload };
}
