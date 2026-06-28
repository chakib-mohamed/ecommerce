import { useSelector } from 'react-redux';
import type { RootState } from '../store';
import { catName as resolveCatName, subName as resolveSubName } from './catalog-helpers';

/** Adapted products from the real `/api`, via the catalog slice. */
export const useCatalogProducts = () => useSelector((s: RootState) => s.catalog.products);

/** Adapted categories from the real `/api`, via the catalog slice. */
export const useCatalogCategories = () => useSelector((s: RootState) => s.catalog.categories);

/** Catalog load status — `'idle' | 'loading' | 'ready' | 'error'`. */
export const useCatalogStatus = () => useSelector((s: RootState) => s.catalog.status);

/** Returns a `catName(id)` resolver bound to the loaded categories. */
export const useCatName = () => {
  const categories = useCatalogCategories();
  return (id: string) => resolveCatName(categories, id);
};

/** Returns a `subName(cat, sub)` resolver bound to the loaded categories. */
export const useSubName = () => {
  const categories = useCatalogCategories();
  return (cat: string, sub: string) => resolveSubName(categories, cat, sub);
};
