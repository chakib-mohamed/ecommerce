import { useEffect, useState } from 'react';
import type { Product } from '../data/catalog';
import type { CartLine } from '../store/StoreCart/store-cart-slice';
import { CartItem, hydrateCart } from './cart';
import { fetchProductById } from './catalog-api';

// Module-level cache of resolved products, keyed by id. Shared across the cart
// drawer, cart page and checkout so re-hydration is cheap — and, crucially, so a
// product added from any (server-paginated) browse page resolves even though it
// was never in the storefront's overview sample.
const cache = new Map<string, Product>();

export interface HydratedCart {
  items: CartItem[];
  /** True while product details for the current lines are still being fetched. */
  loading: boolean;
}

const resolve = (lines: CartLine[]): Product[] =>
  lines.map((l) => cache.get(l.id)).filter((p): p is Product => Boolean(p));

/**
 * Resolves cart lines (`{ id, qty }`) to full items by fetching each product by
 * id and caching it, independent of the storefront catalog sample. Lines whose
 * product no longer exists are dropped.
 */
export const useHydratedCart = (lines: CartLine[]): HydratedCart => {
  const [products, setProducts] = useState<Product[]>(() => resolve(lines));
  const [loading, setLoading] = useState(false);

  const ids = lines.map((l) => l.id).join(',');

  useEffect(() => {
    let cancelled = false;
    setProducts(resolve(lines));

    const missing = Array.from(new Set(lines.map((l) => l.id))).filter((id) => !cache.has(id));
    if (missing.length === 0) {
      setLoading(false);
      return;
    }

    setLoading(true);
    Promise.all(missing.map((id) => fetchProductById(id)))
      .then((fetched) => {
        fetched.forEach((p) => {
          if (p) cache.set(p.id, p);
        });
        if (!cancelled) setProducts(resolve(lines));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
    // Re-run when the set of line ids changes; qty-only changes reuse the cache.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ids]);

  return { items: hydrateCart(lines, products), loading };
};
