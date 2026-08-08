import { restApi } from "../axios-instance";
import { Category, OrderCommand, Product } from "../types/types";

/** Create/update payload for a product. Field names are the wire shape. */
export interface ProductPayload {
  uuid?: string;
  title: string;
  description?: string;
  price: number;
  stock?: number;
  category_id?: number;
  subcategory_id?: number;
}

/** Create payload for a category. Omit `parent_id` for a top-level category. */
export interface CategoryPayload {
  label: string;
  parent_id?: number;
}

/** Update payload for a category (rename / re-parent). */
export interface CategoryUpdatePayload {
  id: number;
  label: string;
  parent_id?: number;
}

export const fetchCategories = (): Promise<Category[]> => {
  return restApi.get("/categories").then((response) => {
    const categories: Category[] = [];
    Object.keys(response.data).forEach((key) => {
      categories.push({
        value: key,
        name: response.data[key],
      });
    });
    return categories;
  });
};

export const createCategory = (createCategoryCommand: CategoryPayload) => {
  return restApi.post("/categories", createCategoryCommand);
};

export const updateCategory = (updateCategoryCommand: CategoryUpdatePayload) => {
  return restApi.put("/categories", updateCategoryCommand);
};

export const deleteCategory = (categoryID: string) => {
  return restApi.delete(`/categories/${categoryID}`);
};

export const createProduct = (product: ProductPayload) => {
  return restApi.post("/products", product);
};

export const fetchProducts = (): Promise<Product[]> => {
  return restApi
    .get("/products")
    .then((response) => response.data)
    .then((products: Record<string, unknown>[]) =>
      products.map((p) => {
        const category = p.category as Record<string, unknown> | undefined;
        return { ...p, category: category?.label ?? "General" } as unknown as Product;
      })
    );
};

export const fetchFeaturedProducts = (): Promise<Product[]> => {
  return restApi
    .get("/products/featured")
    .then((response) => response.data);
};

export const getProduct = (productID: string): Promise<Product> => {
  return restApi
    .get(`/products/${productID}`)
    .then((response) => response.data)
    .then((p: Record<string, unknown>) => {
      const category = p.category as Record<string, unknown> | undefined;
      return { ...p, category: category?.id } as unknown as Product;
    });
};

export const updateProduct = (product: ProductPayload) => {
  return restApi.put("/products", product);
};

export const deleteProduct = (productID: string) => {
  return restApi.delete(`/products/${productID}`);
};

export const loadProductsFormLocalStorage = (): Promise<Product[]> => {
  const cartStr = localStorage.getItem("CART");
  if (cartStr) {
    const cart = JSON.parse(cartStr);
    const productPromises = Object.keys(cart).map((productID) =>
      getProduct(productID)
    );
    return Promise.all(productPromises);
  } else {
    return Promise.resolve([]);
  }
};

export const removeProductFromLocalStorage = (productID: string) => {
  const cartStr = localStorage.getItem("CART");
  if (!cartStr) return;

  const cart = JSON.parse(cartStr);
  delete cart[productID];

  if (Object.keys(cart).length === 0) {
    localStorage.removeItem("CART");
  } else {
    localStorage.setItem("CART", JSON.stringify({ ...cart }));
  }
};

/** The placed order, as echoed back on create (carries the assigned id). */
export interface CreatedOrder {
  id: string;
  price?: number;
}

export const createOrder = (checkoutCommand: OrderCommand): Promise<CreatedOrder> => {
  return restApi.post("/orders", checkoutCommand).then((response) => response.data);
};

/**
 * Commits the order and asks for payment.
 *
 * Resolving does **not** mean the buyer has been charged: it means the order is committed and
 * payment has been requested. The outcome arrives afterwards and can be a decline - it shows up
 * as the order's status, which is why nothing here returns a result to display.
 *
 * `paymentMethod` is an opaque reference to the buyer's chosen method. No card detail passes
 * through this app.
 */
export const confirmOrder = (orderID: string, paymentMethod: string): Promise<void> => {
  return restApi
    .post(`/orders/${orderID}/confirm`, { payment_method: paymentMethod })
    .then(() => undefined);
};

export const deleteOrder = (orderID: string) => {
  return restApi.delete(`/orders/${orderID}`);
};

/** One line item within a placed order (order-history wire shape). */
export interface OrderLineView {
  product_id: string;
  title: string;
  qty: number;
  price: number;
  percentage_off?: number;
}

/**
 * Where an order sits in its lifecycle. All eight states the API can report - an order moves on
 * its own after being confirmed, so anything that treats "not CONFIRMED" as "not finished" reads
 * a paid order as still pending.
 */
export type OrderStatus =
  | "INITIATED"
  | "CONFIRMED"
  | "RESERVED"
  | "PAID"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCELLED"
  | "REFUNDED";

/** A placed order as returned by order history. */
export interface OrderSummary {
  id: string;
  user_id: string;
  creation_date: string;
  price: number;
  status: OrderStatus;
  /** Why the order ended where it did - present on a cancellation, absent otherwise. */
  status_reason?: string;
  products: OrderLineView[];
}

/** A page of order-history results: `x` is the total match count, `y` the current slice. */
export interface OrdersSearchResult {
  x: number;
  y: OrderSummary[];
}

export const fetchOrders = (
  userID: string,
  pageNumber: number,
  pageSize: number
): Promise<OrdersSearchResult> => {
  // `offset` is a zero-based page index; the buyer filter goes on the wire as `user_id`.
  return restApi
    .post(`/orders/search`, { user_id: userID, offset: pageNumber - 1, limit: pageSize })
    .then((resp) => resp.data);
};
