import type { Product } from "../data/catalog";
import type { CartLine } from "../store/StoreCart/store-cart-slice";

/** A cart line resolved against the catalog — product details + line total. */
export interface CartItem extends CartLine {
  key: string;
  product: Product;
  lineTotal: number;
}

/** Resolve raw cart lines to full items, dropping any whose product is gone. */
export const hydrateCart = (lines: CartLine[], products: Product[]): CartItem[] =>
  lines.flatMap((line) => {
    const product = products.find((p) => p.id === line.id);
    if (!product) return [];
    return [
      {
        ...line,
        key: line.id + ":" + line.color,
        product,
        lineTotal: product.price * line.qty,
      },
    ];
  });

/** Total number of units across all lines (for the header cart badge). */
export const cartCount = (lines: CartLine[]): number =>
  lines.reduce((n, l) => n + l.qty, 0);

/** Cart subtotal in whole units. */
export const cartSubtotal = (lines: CartLine[], products: Product[]): number =>
  hydrateCart(lines, products).reduce((s, i) => s + i.lineTotal, 0);

/** Free shipping over $75 (or an empty cart); otherwise a flat $6. */
export const shippingFor = (subtotal: number): number =>
  subtotal > 75 || subtotal === 0 ? 0 : 6;
