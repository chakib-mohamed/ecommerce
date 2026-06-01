import { restApi } from "../axios-instance";
import { Category, OrderCommand, Product, Promotion, PromotionType } from "../types/types";

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

export const fetchPromotions = (): Promise<Promotion[]> => {
  return restApi.get("/promotions").then((resp) => resp.data);
};

export const createCategory = (createCategoryCommand: { label: string }) => {
  return restApi.post("/categories", createCategoryCommand);
};

export const createPromotion = (createPromotionCommand: PromotionType) => {
  const command = { ...createPromotionCommand, productID: createPromotionCommand.product };
  return restApi.post("/promotions", command);
};

export const deleteCategory = (categoryID: string) => {
  return restApi.delete(`/categories/${categoryID}`);
};

export const deletePromotion = (promotionID: string) => {
  return restApi.delete(`/promotions/${promotionID}`);
};

export const createProduct = (product: Record<string, unknown>) => {
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

export const updateProduct = (product: Record<string, unknown>) => {
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

export const createOrder = (checkoutCommand: OrderCommand): Promise<unknown> => {
  return restApi.post("/orders", checkoutCommand).then((response) => response.data);
};

export const deleteOrder = (orderID: string) => {
  return restApi.delete(`/orders/${orderID}`);
};

export const fetchOrders = async (userID: string, pageNumber: number, pageSize: number): Promise<unknown> => {
  const orders = await restApi
    .post(`/orders/search`, { userID, offset: pageNumber - 1, limit: pageSize })
    .then((resp) => resp.data);
  return orders;
};
