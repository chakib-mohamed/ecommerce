import { configureStore } from "@reduxjs/toolkit";
import { combineReducers } from "redux";
import catalogReducer from "./Catalog/catalog-slice";
import authenticationReducer from "./Login/login-slice";
import storeCartReducer, { persistStoreCart } from "./StoreCart/store-cart-slice";

const rootReducer = combineReducers({
  catalog: catalogReducer,
  login: authenticationReducer,
  storeCart: storeCartReducer,
});

export const store = configureStore({ reducer: rootReducer });

// Persist the storefront cart lines to localStorage on every change.
let lastItems = store.getState().storeCart.items;
store.subscribe(() => {
  const { items } = store.getState().storeCart;
  if (items !== lastItems) {
    lastItems = items;
    persistStoreCart(items);
  }
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
