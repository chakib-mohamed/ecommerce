import { useDispatch } from "react-redux";
import { toast } from "react-toastify";
import type { Product, Swatch } from "../data/catalog";
import type { AppDispatch } from "../store";
import { addToCart, openDrawer } from "../store/StoreCart/store-cart-slice";

/**
 * Returns an `add(product, color?, qty?)` callback that pushes a line onto the
 * storefront cart, pops the cart drawer, and confirms with a toast — the
 * design's add-to-cart behaviour, in one place.
 */
export function useAddToCart() {
  const dispatch = useDispatch<AppDispatch>();
  return (product: Product, color?: Swatch, qty = 1) => {
    const col = color ?? product.colors[0];
    dispatch(addToCart({ id: product.id, color: col, qty }));
    dispatch(openDrawer());
    toast.success(`Added ${product.name}`);
  };
}
