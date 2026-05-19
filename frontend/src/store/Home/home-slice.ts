import { AnyAction, createSlice, PayloadAction } from "@reduxjs/toolkit";
import type { ThunkDispatch } from "@reduxjs/toolkit";
import { service } from "../../services";
import { Product } from "../../types/types";

type LocalDispatch = ThunkDispatch<unknown, undefined, AnyAction>;

interface HomeState {
  products: Product[];
  loading: boolean;
}

const INITIAL_STATE: HomeState = {
  products: [],
  loading: false,
};

const homeSlice = createSlice({
  name: "home",
  initialState: INITIAL_STATE,
  reducers: {
    fetchProductsStart: (state) => {
      state.loading = true;
    },
    fetchProductsSuccess: (state, action: PayloadAction<{ products: Product[] }>) => {
      state.loading = false;
      state.products = action.payload.products;
    },
    fetchProductsFail: (state) => {
      state.loading = false;
    },
    resetState: () => INITIAL_STATE,
  },
});

export const fecthProducts = () => {
  return (dispatch: LocalDispatch) => {
    dispatch(homeSlice.actions.fetchProductsStart());
    service
      .fetchFeaturedProducts()
      .then((products) => {
        dispatch(
          homeSlice.actions.fetchProductsSuccess({ products })
        );
      })
      .catch((error: unknown) => {
        dispatch(homeSlice.actions.fetchProductsFail());
        console.error("Failed to fetch featured products", error);
      });
  };
};

export default homeSlice.reducer;
export const { resetState } = homeSlice.actions;
