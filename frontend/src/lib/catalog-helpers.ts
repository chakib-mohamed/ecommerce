import { categories } from '../data/catalog';

/** Resolve a category id to its display name (falls back to the id). */
export const catName = (id: string): string =>
  categories.find((c) => c.id === id)?.name ?? id;

/** Resolve a subcategory id (within a category) to its display name. */
export const subName = (cat: string, sub: string): string => {
  const c = categories.find((x) => x.id === cat);
  return c?.subs.find((x) => x.id === sub)?.name ?? sub;
};
