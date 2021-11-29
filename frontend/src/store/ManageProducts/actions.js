import { service } from "../../services";

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
  return (dispatch) => {
    dispatch(fetchProductsStart());
    service
      .fetchProducts()
      .then((products) => {
        dispatch(fetchProductSuccess(products));
      })
      .catch((error) => {
        dispatch(fetchProductsFail(error));
      });
  };
};

const fetchProductsStart = () => {
  return {
    type: FETCH_PRODUCTS_START1,
  };
};

const fetchProductSuccess = (products) => {
  return {
    type: FETCH_PRODUCTS_SUCCESS1,
    products: products,
  };
};
const fetchProductsFail = (error) => {
  return {
    type: FETCH_PRODUCTS_FAIL1,
    error: error,
  };
};

const updateProductSuccess = () => {
  return {
    type: UPDATE_PRODUCT_SUCCESS,
  };
};

export const updateProduct = (product) => {
  return (dispatch) => {
    service.updateProduct(product).then((_) => {
      dispatch(updateProductSuccess());
      dispatch(fetchProducts());
    });
  };
};

export const openUpdateProductModal = (productID) => {
  return (dispatch) => {
    dispatch({ type: OPEN_UPDATE_PRODUCT_MODAL, productID: productID });
    service.getProduct(productID).then((product) => {
      dispatch(getProductSuccess(product));
    });
  };
};

const getProductSuccess = (product) => {
  return {
    type: GET_PRODUCT_SUCCESS,
    product: product,
  };
};

// const mapSnapshotToProduct = (productID, data) => {
//   let product = {
//     id: productID,
//     title: data[productID].title,
//     description: data[productID].description,
//     price: data[productID].price,
//     image: data[productID].image,
//     category: data[productID].category,
//   };

//   return product;
// };

export const closeUpdateProductModal = () => {
  return {
    type: CLOSE_UPDATE_PRODUCT_MODAL,
  };
};

export const openDeleteProductModal = (productID) => {
  return {
    type: OPEN_DELETE_PRODUCT_MODAL,
    productID: productID,
  };
};

export const closeDeleteProductModal = () => {
  return {
    type: CLOSE_DELETE_PRODUCT_MODAL,
  };
};

const deleteProductSuccess = () => {
  return { type: DELETE_PRODUCT_SUCCESS };
};

export const deleteProduct = (productID) => {
  return (dispatch) => {
    service.deleteProduct(productID).then((_) => {
      dispatch(deleteProductSuccess());
      dispatch(fetchProducts());
    });
  };
};

export const fetchCategories = () => {
  return (dispatch) => {
    service.fetchCategories().then((categories) => {
      dispatch(fetchCategoriesSuccess(categories));
    });
  };
};

const fetchCategoriesSuccess = (data) => {
  return {
    type: FETCH_CATEGORIES_SUCCESS,
    categories: data,
  };
};
