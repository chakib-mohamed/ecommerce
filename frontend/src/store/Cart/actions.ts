import { Dispatch } from "redux";
import { service } from "../../services";
import { OrderCommand, Product } from "../../types/types";

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

const loadProductsStart = () => ({ type: LOAD_PRODUCTS_START });

const loadProductsSuccess = (products: (Product & { qty: number})[]) => ({
  type: LOAD_PRODUCTS_SUCCESS,
  products,
});

export const loadProductsFail = (error: unknown) => ({
  type: LOAD_PRODUCTS_FAIL,
  error,
});

export const loadProducts = () => {
  return (dispatch: Dispatch) => {
    dispatch(loadProductsStart());
    service
      .loadProductsFormLocalStorage()
      .then((products) => {
        const cartStr = localStorage.getItem("CART");
        const cart = cartStr ? JSON.parse(cartStr) : {};
        const productsWithQty = products.map((product) => ({
          ...product,
          qty: cart[product.id] || 1,
        }));

        dispatch(loadProductsSuccess(productsWithQty));
      })
      .catch((error) => {
        dispatch(loadProductsFail(error));
      });
  };
};

export const removeProductFromCart = (productID: string) => {
  service.removeProductFromLocalStorage(productID);
  return {
    type: REMOVE_PRODUCT_FROM_CART,
    productID,
  };
};

const checkoutStart = () => ({ type: CHECKOUT_START });

const checkoutSuccess = (order: unknown) => {
  localStorage.removeItem("CART");
  return {
    type: CHECKOUT_SUCCESS,
    order,
  };
};

export const openCheckouModal = () => ({ type: OPEN_CHECKOUT_MODAL });

export const closeCheckouModal = () => ({ type: CLOSE_CHECKOUT_MODAL });

export const resetState = () => ({ type: RESET_CART_STATE });

export const checkout = (checkoutCommand: OrderCommand) => {
  return async (dispatch: Dispatch) => {
    dispatch(checkoutStart());
    try {
      const order = await service.createOrder(checkoutCommand);
      dispatch(checkoutSuccess(order));
    } catch (error) {
      dispatch({ type: CHECKOUT_FAIL, error });
    }
  };
};

export const closeSuccessMessage = () => ({ type: CLOSE_SUCCESS_MESSAGE });

export const updateQty = (productID: string, qty: number) => ({ 
  type: UPDTATE_QTY, 
  productID, 
  qty 
});
