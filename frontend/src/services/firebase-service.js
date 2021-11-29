import axios from "../axios-instance";
import { db } from "./firebase-config";
import { auth } from "./firebase-config";

export const fetchCategories = () => {
  return axios.get("/categories.json");
};

export const fetchPromotions = () => {
  return db
    .ref("promotions")
    .once("value")
    .then((snapshot) => {
      let promotions = [];
      snapshot.forEach((snap) => {
        promotions.push({ ...snap.val(), id: snap.key });
      });

      return promotions;
    });
};

export const createCategory = (createCategoryCommand) => {
  return fetchCategories()
    .then((response) => {
      let newValue = { ...response.data };
      newValue[createCategoryCommand.label.replace(" ", "")] =
        createCategoryCommand.label;

      return newValue;
    })
    .then((newValue) => db.ref("categories").set(newValue));
};

export const deleteCategory = (categoryID) => {
  return db.ref("categories").child(categoryID).remove();
};

export const createProduct = (product) => {
  return axios.post("/products.json", product);
};

// export const loadProducts = () => {
//   return axios.get("/products.json");
// };

export const fetchProducts = () => {
  return db
    .ref("products")
    .once("value")
    .then((snapshot) => {
      let products = [];
      snapshot.forEach((snap) => {
        products.push({ ...snap.val(), id: snap.key });
      });

      return products;
    });
};

export const getProduct = (productID) => {
  // return axios.get("/products.json/" + productID);
  return db
    .ref("products")
    .orderByKey()
    .equalTo(productID)
    .once("value")
    .then((snapshot) => {
      const data = snapshot.val() || [];
      return { ...data[productID], id: productID };
    });
};

export const updateProduct = (product) => {
  return db.ref("products").child(product.id).update(product);
};

export const deleteProduct = (productID) => {
  return db.ref("products").child(productID).remove();
};

export const loadProductsFormLocalStorage = () => {
  if (localStorage.getItem("CART")) {
    let cart = JSON.parse(localStorage.getItem("CART"));

    let productPromises = Object.keys(cart).map((productID) =>
      getProduct(productID)
    );
    return Promise.all(productPromises);
  } else {
    return Promise.resolve();
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

export const createOrder = async (checkoutCommand) => {
  const currentUser = auth.currentUser;
  let order = { ...checkoutCommand, userID: currentUser.uid };
  let snapshot = await db.ref("orders").push(order).once("value");

  return { ...snapshot.val(), id: snapshot.key };
};

export const deleteOrder = (orderID) => {
  return db.ref("orders").child(orderID).remove();
};

export const fetchOrders = async (userID, pageNumber, pageSize) => {
  // let startAt = pageNumber === 1 ? 0 : (pageNumber - 1) * pageSize;
  let snapshot = await db
    .ref("orders")
    .orderByChild("userID")
    .equalTo(userID)
    // .startAt(startAt)
    // .limitToFirst(pageSize)
    .once("value");

  let orders = [];

  let productsPromises = [];
  snapshot.forEach((snap) => {
    let order = { ...snap.val(), id: snap.key, products: [] };

    for (let p of snap.val().products) {
      productsPromises.push(
        getProduct(p.productID).then((product) => {
          order.products.push({ ...p, ...product });
        })
      );
    }

    orders.push(order);
  });

  await Promise.all(productsPromises);
  return { x: orders.length, y: orders };
};
