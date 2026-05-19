import { AnyAction } from "redux";
import type { ThunkDispatch } from "@reduxjs/toolkit";
import { Dispatch } from "redux";
import { service } from "../../services";
import { Category, Product } from "../../types/types";

type LocalDispatch = ThunkDispatch<unknown, undefined, AnyAction>;

export const FETCH_PRODUCTS_START1 = "FETCH_PRODUCTS_START1";
export const FETCH_PRODUCTS_SUCCESS1 = "FETCH_PRODUCTS_SUCCESS1";
export const FETCH_PRODUCTS_FAIL1 = "FETCH_PRODUCTS_FAIL1";

export const OPEN_UPDATE_PRODUCT_MODAL = "OPEN_UPDATE_PRODUCT_MODAL";
export const CLOSE_UPDATE_PRODUCT_MODAL = "CLOSE_UPDATE_PRODUCT_MODAL";
export const GET_PRODUCT_SUCCESS = "GET_PRODUCT_SUCCESS";
export const UPDATE_PRODUCT_SUCCESS = "UPDATE_PRODUCT_SUCCESS";

export const OPEN_DELETE_PRODUCT_MODAL = "OPEN_DELETE_PRODUCT_MODAL";
export const CLOSE_DELETE_PRODUCT_MODAL = "CLOSE_DELETE_PRODUCT_MODAL";
export const DELETE_PRODUCT_SUCCESS = "DELETE_PRODUCT_SUCCESS";

export const FETCH_CATEGORIES_SUCCESS = "FETCH_CATEGORIES_SUCCESS";
export const FETCH_CATEGORIES_FAIL = "FETCH_CATEGORIES_FAIL";

export const fetchProducts = () => {
  return (dispatch: LocalDispatch) => {
    dispatch(fetchProductsStart());
    service
      .fetchProducts()
      .then((products) => {
        dispatch(fetchProductSuccess(products));
      })
      .catch((error: unknown) => {
        dispatch(fetchProductsFail(error));
      });
  };
};

const fetchProductsStart = () => ({ type: FETCH_PRODUCTS_START1 });

const fetchProductSuccess = (products: Product[]) => ({
  type: FETCH_PRODUCTS_SUCCESS1,
  products,
});

const fetchProductsFail = (error: unknown) => ({
  type: FETCH_PRODUCTS_FAIL1,
  error,
});

const updateProductSuccess = () => ({ type: UPDATE_PRODUCT_SUCCESS });

export const updateProduct = (product: Product) => {
  return (dispatch: LocalDispatch) => {
    service.updateProduct(product as unknown as Record<string, unknown>).then(() => {
      dispatch(updateProductSuccess());
      dispatch(fetchProducts());
    });
  };
};

export const openUpdateProductModal = (productID: string) => {
  return (dispatch: Dispatch) => {
    dispatch({ type: OPEN_UPDATE_PRODUCT_MODAL, productID });
    service.getProduct(productID).then((product) => {
      dispatch(getProductSuccess(product));
    });
  };
};

const getProductSuccess = (product: Product) => ({
  type: GET_PRODUCT_SUCCESS,
  product,
});

export const closeUpdateProductModal = () => ({ type: CLOSE_UPDATE_PRODUCT_MODAL });

export const openDeleteProductModal = (productID: string) => ({
  type: OPEN_DELETE_PRODUCT_MODAL,
  productID,
});

export const closeDeleteProductModal = () => ({ type: CLOSE_DELETE_PRODUCT_MODAL });

const deleteProductSuccess = () => ({ type: DELETE_PRODUCT_SUCCESS });

export const deleteProduct = (productID: string) => {
  return (dispatch: LocalDispatch) => {
    service.deleteProduct(productID).then(() => {
      dispatch(deleteProductSuccess());
      dispatch(fetchProducts());
    });
  };
};

export const fetchCategories = () => {
  return (dispatch: Dispatch) => {
    service.fetchCategories().then((categories) => {
      dispatch(fetchCategoriesSuccess(categories));
    });
  };
};

const fetchCategoriesSuccess = (data: Category[]) => ({
  type: FETCH_CATEGORIES_SUCCESS,
  categories: data,
});
