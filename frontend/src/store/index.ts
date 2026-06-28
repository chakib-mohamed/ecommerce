import { configureStore } from "@reduxjs/toolkit";
import { combineReducers } from "redux";
import cartReducers from "./Cart/reducers";
import catalogReducer from "./Catalog/catalog-slice";
import homeReducer from "./Home/home-slice";
import authenticationReducer from "./Login/login-slice";
import manageProductsReducers from "./ManageProducts/reducers";
import ordersReducers from "./Orders/reducers";
import storeCartReducer, { persistStoreCart } from "./StoreCart/store-cart-slice";

const rootReducer = combineReducers({
  home: homeReducer,
  catalog: catalogReducer,
  manageProducts: manageProductsReducers,
  login: authenticationReducer,
  cart: cartReducers,
  orders: ordersReducers,
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
