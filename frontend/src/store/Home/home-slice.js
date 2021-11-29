import { createSlice } from "@reduxjs/toolkit";
import { service } from "../../services";

const INITIAL_STATE = {
  products: [],
  loading: false,
};

const homeSlice = createSlice({
  name: "home",
  initialState: INITIAL_STATE,
  reducers: {
    fetchProductsStart: (state, action) => {
      state.loading = true;
    },
    fetchProductsSuccess: (state, action) => {
      state.loading = false;
      state.products = action.payload.products;
    },
    fetchProductsFail: (state, action) => {
      state.loading = false;
    },
    resetState: (state) => INITIAL_STATE,
  },
});

export const fecthProducts = () => {
  return (dispatch) => {
    dispatch(homeSlice.actions.fetchProductsStart());
    service
      .fetchProductsSnapshot()
      .then((products) => {
        dispatch(
          homeSlice.actions.fetchProductsSuccess({ products: products })
        );
      })
      .catch((error) => {
        dispatch(homeSlice.actions.fetchProductsFail({ error: error }));
      });
  };
};

export default homeSlice.reducer;
export const { resetState } = homeSlice.actions;
