import * as actions from "./actions";
import { updateObject } from "../../services/utils";

const initialState = {
  products: [],
  loading: false,
  productID: null,
  product: null,
  displayEditModal: false,
  displayDeleteModalConfirmation: false,
  categories: null,
};

const fetchProductsStart = (state) => {
  return updateObject(state, { loading: true });
};

const fetchProductsSuccess = (state, action) => {
  return updateObject(state, {
    products: action.products,
    loading: false,
  });
};

const fetchProductsFail = (state, action) => {
  return updateObject(state, { loading: false });
};

const updateProductSuccess = (state, action) => {
  return updateObject(state, { displayEditModal: false });
};

const getProductSuccess = (state, action) => {
  return updateObject(state, { product: action.product });
};

const openUpdateProductModal = (state, action) => {
  return updateObject(state, {
    displayEditModal: true,
    productID: action.productID,
  });
};

const closeUpdateProductModal = (state, action) => {
  return updateObject(state, {
    displayEditModal: false,
  });
};

const openDeleteProductModal = (state, action) => {
  return updateObject(state, {
    displayDeleteModalConfirmation: true,
    productID: action.productID,
  });
};

const closeDeleteProductModal = (state, action) => {
  return updateObject(state, {
    displayDeleteModalConfirmation: false,
  });
};

const deleteProductSuccess = (state, action) => {
  return updateObject(state, { displayDeleteModalConfirmation: false });
};

const fetchCategoriesSuccess = (state, action) => {
  return updateObject(state, { categories: action.categories });
};

const reducer = (state = initialState, action) => {
  switch (action.type) {
    case actions.FETCH_PRODUCTS_START1:
      return fetchProductsStart(state, action);
    case actions.FETCH_PRODUCTS_SUCCESS1:
      return fetchProductsSuccess(state, action);
    case actions.FETCH_PRODUCTS_FAIL1:
      return fetchProductsFail(state, action);

    case actions.OPEN_UPDATE_PRODUCT_MODAL:
      return openUpdateProductModal(state, action);
    case actions.CLOSE_UPDATE_PRODUCT_MODAL:
      return closeUpdateProductModal(state, action);

    case actions.GET_PRODUCT_SUCCESS:
      return getProductSuccess(state, action);
    case actions.UPDATE_PRODUCT_SUCCESS:
      return updateProductSuccess(state, action);

    case actions.OPEN_DELETE_PRODUCT_MODAL:
      return openDeleteProductModal(state, action);
    case actions.CLOSE_DELETE_PRODUCT_MODAL:
      return closeDeleteProductModal(state, action);
    case actions.DELETE_PRODUCT_SUCCESS:
      return deleteProductSuccess(state, action);

    case actions.FETCH_CATEGORIES_SUCCESS:
      return fetchCategoriesSuccess(state, action);

    default:
      return state;
  }
};

export default reducer;
