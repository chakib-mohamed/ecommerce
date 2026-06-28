import { useDispatch } from "react-redux";
import { toast } from "react-toastify";
import type { Product } from "../data/catalog";
import type { AppDispatch } from "../store";
import { addToCart, openDrawer } from "../store/StoreCart/store-cart-slice";

/**
 * Returns an `add(product, qty?)` callback that pushes a line onto the
 * storefront cart, pops the cart drawer, and confirms with a toast — the
 * design's add-to-cart behaviour, in one place.
 */
export function useAddToCart() {
  const dispatch = useDispatch<AppDispatch>();
  return (product: Product, qty = 1) => {
    dispatch(addToCart({ id: product.id, qty }));
    dispatch(openDrawer());
    toast.success(`Added ${product.name}`);
  };
}
