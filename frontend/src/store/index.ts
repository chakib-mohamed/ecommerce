import { configureStore } from "@reduxjs/toolkit";
import { combineReducers } from "redux";
import cartReducers from "./Cart/reducers";
import homeReducer from "./Home/home-slice";
import authenticationReducer from "./Login/login-slice";
import manageProductsReducers from "./ManageProducts/reducers";
import ordersReducers from "./Orders/reducers";

const rootReducer = combineReducers({
  home: homeReducer,
  manageProducts: manageProductsReducers,
  login: authenticationReducer,
  cart: cartReducers,
  orders: ordersReducers,
});

export const store = configureStore({ reducer: rootReducer });

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
