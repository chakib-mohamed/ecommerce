import type { Category } from '../data/catalog';

/** Resolve a category id to its display name (falls back to the id). */
export const catName = (categories: Category[], id: string): string =>
  categories.find((c) => c.id === id)?.name ?? id;

/**
 * Resolve a subcategory id (within a category) to its display name. When the
 * product has no subcategory (the backend serves a flat catalog), falls back to
 * the category name so the label is never blank.
 */
export const subName = (categories: Category[], cat: string, sub: string): string => {
  const c = categories.find((x) => x.id === cat);
  if (!sub) return c?.name ?? cat;
  return c?.subs.find((x) => x.id === sub)?.name ?? sub;
};
