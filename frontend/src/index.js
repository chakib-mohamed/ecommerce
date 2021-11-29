import { configureStore } from "@reduxjs/toolkit";
import React from "react";
import ReactDOM from "react-dom";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import { combineReducers } from "redux";
import "../node_modules/bootstrap/dist/css/bootstrap.min.css";
import "../node_modules/font-awesome/css/font-awesome.min.css";
import App from "./App";
import "./index.css";
// import authenticationReducers from "./store/Login/reducers";
import cartReducers from "./store/Cart/reducers";
import homeReducer from "./store/Home/home-slice";
import authenticationReducer from "./store/Login/login-slice";
// import homeReducers from "./store/Home/reducers";
import manageProductsReducers from "./store/ManageProducts/reducers";
import ordersReducers from "./store/Orders/reducers";

// const composeEnhancers = window.__REDUX_DEVTOOLS_EXTENSION_COMPOSE__ || compose;

const rootReducer = combineReducers({
  home: homeReducer,
  manageProducts: manageProductsReducers,
  login: authenticationReducer,
  cart: cartReducers,
  orders: ordersReducers,
});

// Redux toolkit
const store = configureStore({ reducer: rootReducer });

// const store = createStore(
//   rootReducer,
//   composeEnhancers(applyMiddleware(thunk))
// );

ReactDOM.render(
  <React.StrictMode>
    <Provider store={store}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </Provider>
  </React.StrictMode>,
  document.getElementById("root")
);
