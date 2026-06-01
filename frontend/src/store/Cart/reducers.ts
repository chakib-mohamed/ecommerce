import { updateObject } from "../../services/utils";
import { Product } from "../../types/types";
import * as actions from "./actions";

type LooseAction = { type: string } & Record<string, unknown>;

interface CartState {
  products: (Product & { qty: number })[];
  isLoading: boolean;
  displayCheckoutModal: boolean;
  displaySuccessMessage: boolean;
}

const initialState: CartState = {
  products: [],
  isLoading: true,
  displayCheckoutModal: false,
  displaySuccessMessage: false,
};

const reducer = (state = initialState, action: LooseAction): CartState => {
  switch (action.type) {
    case actions.LOAD_PRODUCTS_START:
      return updateObject(state, { isLoading: true });
    case actions.LOAD_PRODUCTS_SUCCESS:
      return updateObject(state, {
        products: action.products as (Product & { qty: number })[],
        isLoading: false,
      });
    case actions.LOAD_PRODUCTS_FAIL:
      return updateObject(state, { isLoading: false });

    case actions.REMOVE_PRODUCT_FROM_CART:
      return updateObject(state, { 
        products: state.products.filter((p) => p.id !== action.productID) 
      });

    case actions.OPEN_CHECKOUT_MODAL:
      return updateObject(state, { displayCheckoutModal: true });
    case actions.CLOSE_CHECKOUT_MODAL:
      return updateObject(state, { displayCheckoutModal: false });

    case actions.CHECKOUT_START:
      return state;
    case actions.CHECKOUT_SUCCESS:
      return updateObject(state, {
        displaySuccessMessage: true,
        displayCheckoutModal: false,
        products: [],
      });
    case actions.CHECKOUT_FAIL:
      return updateObject(state, {
        displaySuccessMessage: false,
        displayCheckoutModal: false,
      });

    case actions.CLOSE_SUCCESS_MESSAGE:
      return updateObject(state, { displaySuccessMessage: false });

    case actions.UPDTATE_QTY:
      return updateObject(state, {
        products: state.products.map((item) =>
          item.id === action.productID ? { ...item, qty: action.qty as number } : item
        ),
      });

    case actions.RESET_CART_STATE:
      return { ...initialState };

    default:
      return state;
  }
};

export default reducer;
