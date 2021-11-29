import * as actions from "./actions";
import { updateObject } from "../../services/utils";

const initialState = {
  products: [],
  isLoading: true,
  displayCheckoutModal: false,
  displaySuccessMessage: false,
};

const loadProductsStart = (state) => {
  return updateObject(state, { isLoading: true });
};

const loadProductsSuccess = (state, action) => {
  return updateObject(state, {
    products: action.products,
    isLoading: false,
  });
};

const loadProductsFail = (state, action) => {
  return updateObject(state, { isLoading: false });
};

const removeProductFromCart = (state, action) => {
  let products = state.products.filter((p) => p.id !== action.productID);
  return updateObject(state, { products: products });
};

const checkoutStart = (state, action) => {
  return updateObject(state);
};

const checkoutSuccess = (state, action) => {
  return updateObject(state, {
    displaySuccessMessage: true,
    displayCheckoutModal: false,
    products: [],
  });
};

const checkoutFail = (state, action) => {
  return updateObject(state, {
    displaySuccessMessage: false,
    displayCheckoutModal: false,
  });
};

const openCheckouModal = (state, action) => {
  return updateObject(state, {
    displayCheckoutModal: true,
  });
};

const closeCheckouModal = (state, action) => {
  return updateObject(state, {
    displayCheckoutModal: false,
  });
};

const closeSuccessMessage = (state, action) => {
  return updateObject(state, {
    displaySuccessMessage: false,
  });
};

const updateQty = (state, action) => {
  let newState = { ...state };

  let products = newState.products.map((item, index) => {
    if (item.id !== action.productID) {
      return item;
    } else {
      return { ...item, qty: action.qty };
    }
  });

  newState.products = products;
  return updateObject(state, newState);
};

const reducer = (state = initialState, action) => {
  switch (action.type) {
    case actions.LOAD_PRODUCTS_START:
      return loadProductsStart(state, action);
    case actions.LOAD_PRODUCTS_SUCCESS:
      return loadProductsSuccess(state, action);
    case actions.LOAD_PRODUCTS_FAIL:
      return loadProductsFail(state, action);

    case actions.REMOVE_PRODUCT_FROM_CART:
      return removeProductFromCart(state, action);

    case actions.OPEN_CHECKOUT_MODAL:
      return openCheckouModal(state, action);
    case actions.CLOSE_CHECKOUT_MODAL:
      return closeCheckouModal(state, action);

    case actions.CHECKOUT_START:
      return checkoutStart(state, action);
    case actions.CHECKOUT_SUCCESS:
      return checkoutSuccess(state, action);
    case actions.CHECKOUT_FAIL:
      return checkoutFail(state, action);

    case actions.CLOSE_SUCCESS_MESSAGE:
      return closeSuccessMessage(state, action);

    case actions.UPDTATE_QTY:
      return updateQty(state, action);

    case actions.RESET_CART_STATE:
      return { ...initialState };

    default:
      return state;
  }
};

export default reducer;
