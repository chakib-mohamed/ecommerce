import { restApi } from "../axios-instance";

export const fetchCategories = () => {
  return restApi.get("/categories").then((response) => {
    const categories = [];
    Object.keys(response.data).forEach((key) => {
      categories.push({
        value: key,
        name: response.data[key],
      });
    });

    return categories;
  });
};

export const fetchPromotions = () => {
  return restApi.get("/promotions").then((resp) => resp.data);
};

export const createCategory = (createCategoryCommand) => {
  return restApi.post("/categories", createCategoryCommand);
};

export const createPromotion = (createPromotionCommand) => {
  createPromotionCommand.productID = createPromotionCommand.product;
  return restApi.post("/promotions", createPromotionCommand);
};

export const deleteCategory = (categoryID) => {
  return restApi.delete(`/categories/${categoryID}`);
};

export const deletePromotion = (promotionID) => {
  return restApi.delete(`/promotions/${promotionID}`);
};

export const createProduct = (product) => {
  return restApi.post("/products", product);
};

export const fetchProducts = () => {
  return restApi
    .get("/products")
    .then((response) => response.data)
    .then((products) =>
      products.map((p) => {
        p.category = p.category.label;
        return p;
      })
    );
};

export const fetchProductsSnapshot = () => {
  return restApi
    .get("/products/snapshot")
    .then((response) => response.data)
    .then((products) =>
      products.map((p) => {
        p.category = p.category.label;
        return p;
      })
    );
};

export const getProduct = (productID) => {
  return restApi
    .get(`/products/${productID}`)
    .then((response) => response.data)
    .then((p) => {
      p.category = p.category.id;
      return p;
    });
};

export const updateProduct = (product) => {
  return restApi.put("/products", product);
};

export const deleteProduct = (productID) => {
  return restApi.delete(`/products/${productID}`);
};

export const loadProductsFormLocalStorage = () => {
  if (localStorage.getItem("CART")) {
    let cart = JSON.parse(localStorage.getItem("CART"));

    let productPromises = Object.keys(cart).map((productID) =>
      getProduct(productID)
    );
    return Promise.all(productPromises);
  } else {
    return Promise.resolve([]);
  }
};

export const removeProductFromLocalStorage = (productID) => {
  let cart = JSON.parse(localStorage.getItem("CART"));
  delete cart[productID];
  if (Object.keys(cart).length === 0) {
    localStorage.removeItem("CART");
  } else {
    localStorage.setItem("CART", JSON.stringify({ ...cart }));
  }
};

export const createOrder = (checkoutCommand) => {
  let order = { ...checkoutCommand };
  return restApi.post("/orders", order).then((response) => response.data);
};

export const deleteOrder = (orderID) => {
  return restApi.delete(`/orders/${orderID}`);
};

export const fetchOrders = async (userID, pageNumber, pageSize) => {
  let orders = await restApi
    .post(`/orders/search`, { userID, offset: pageNumber - 1, limit: pageSize })
    .then((resp) => resp.data);

  // for (let order of orders.y) {
  //   let products = [];
  //   for (let p of order.products) {
  //     await getProduct(p.productID).then((product) => {
  //       products.push({ ...p, ...product });
  //     });
  //   }

  //   order.products = products;
  // }
  return orders;
};
