import { service } from "../../services";

export const LOAD_PRODUCTS_START = "LOAD_PRODUCTS_START";
export const LOAD_PRODUCTS_SUCCESS = "LOAD_PRODUCTS_SUCCESS";
export const LOAD_PRODUCTS_FAIL = "LOAD_PRODUCTS_FAIL";
export const RESET_CART_STATE = "RESET_CART_STATE";
export const REMOVE_PRODUCT_FROM_CART = "REMOVE_PRODUCT_FROM_CART";

export const CHECKOUT_START = "CHECKOUT_START";
export const CHECKOUT_SUCCESS = "CHECKOUT_SUCCESS";
export const CHECKOUT_FAIL = "CHECKOUT_FAIL";

export const OPEN_CHECKOUT_MODAL = "OPEN_CHECKOUT_MODAL";
export const CLOSE_CHECKOUT_MODAL = "CLOSE_CHECKOUT_MODAL";

export const CLOSE_SUCCESS_MESSAGE = "CLOSE_SUCCESS_MESSAGE";

export const UPDTATE_QTY = "UPDTATE_QTY";

const loadProductsStart = () => {
  return {
    type: LOAD_PRODUCTS_START,
  };
};

const loadProductsSuccess = (products) => {
  return {
    type: LOAD_PRODUCTS_SUCCESS,
    products: products,
  };
};

export const loadProductsFail = (error) => {
  return {
    type: LOAD_PRODUCTS_FAIL,
    error: error,
  };
};

export const loadProducts = () => {
  return (dispatch) => {
    dispatch(loadProductsStart());
    service
      .loadProductsFormLocalStorage()
      .then((products) => {
        let cart = JSON.parse(localStorage.getItem("CART"));
        let productsWithQty = products.map((product) => {
          let productWithQty = {
            ...product,
            qty: cart[product.id],
          };

          return productWithQty;
        });

        dispatch(loadProductsSuccess(productsWithQty));
      })
      .catch((error) => {
        dispatch(loadProductsFail(error));
      });
  };
};

export const removeProductFromCart = (productID) => {
  service.removeProductFromLocalStorage(productID);
  return {
    type: REMOVE_PRODUCT_FROM_CART,
    productID: productID,
  };
};

const checkoutStart = () => {
  return {
    type: CHECKOUT_START,
  };
};

const checkoutSuccess = (order) => {
  localStorage.removeItem("CART");
  return {
    type: CHECKOUT_SUCCESS,
    order: order,
  };
};

export const openCheckouModal = () => {
  return {
    type: OPEN_CHECKOUT_MODAL,
  };
};

export const closeCheckouModal = () => {
  return {
    type: CLOSE_CHECKOUT_MODAL,
  };
};

export const resetState = () => {
  return {
    type: RESET_CART_STATE,
  };
};

export const checkout = (checkoutCommand) => {
  return async (dispatch) => {
    dispatch(checkoutStart());
    let order = await service.createOrder(checkoutCommand);
    dispatch(checkoutSuccess(order));
  };
};

export const closeSuccessMessage = () => {
  return { type: CLOSE_SUCCESS_MESSAGE };
};

export const updateQty = (productID, qty) => {
  return { type: UPDTATE_QTY, productID: productID, qty: qty };
};
