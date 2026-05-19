import { updateObject } from "../../services/utils";
import { Category, Product } from "../../types/types";
import * as actions from "./actions";

type LooseAction = { type: string } & Record<string, unknown>;

interface ManageProductsState {
  products: Product[];
  loading: boolean;
  productID: string | null;
  product: Product | null;
  displayEditModal: boolean;
  displayDeleteModalConfirmation: boolean;
  categories: Category[] | null;
}

const initialState: ManageProductsState = {
  products: [],
  loading: false,
  productID: null,
  product: null,
  displayEditModal: false,
  displayDeleteModalConfirmation: false,
  categories: null,
};

const reducer = (state = initialState, action: LooseAction): ManageProductsState => {
  switch (action.type) {
    case actions.FETCH_PRODUCTS_START1:
      return updateObject(state, { loading: true });
    case actions.FETCH_PRODUCTS_SUCCESS1:
      return updateObject(state, { products: action.products, loading: false });
    case actions.FETCH_PRODUCTS_FAIL1:
      return updateObject(state, { loading: false });

    case actions.OPEN_UPDATE_PRODUCT_MODAL:
      return updateObject(state, { displayEditModal: true, productID: action.productID });
    case actions.CLOSE_UPDATE_PRODUCT_MODAL:
      return updateObject(state, { displayEditModal: false });

    case actions.GET_PRODUCT_SUCCESS:
      return updateObject(state, { product: action.product });
    case actions.UPDATE_PRODUCT_SUCCESS:
      return updateObject(state, { displayEditModal: false });

    case actions.OPEN_DELETE_PRODUCT_MODAL:
      return updateObject(state, { displayDeleteModalConfirmation: true, productID: action.productID });
    case actions.CLOSE_DELETE_PRODUCT_MODAL:
      return updateObject(state, { displayDeleteModalConfirmation: false });
    case actions.DELETE_PRODUCT_SUCCESS:
      return updateObject(state, { displayDeleteModalConfirmation: false });

    case actions.FETCH_CATEGORIES_SUCCESS:
      return updateObject(state, { categories: action.categories });

    default:
      return state;
  }
};

export default reducer;
