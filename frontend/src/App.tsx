import React, { useEffect } from "react";
import { useIdleTimer } from "react-idle-timer";
import { useDispatch } from "react-redux";
import { Route, Routes } from "react-router-dom";
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import AddProduct from "./containers/AddProduct/AddProduct";
import Cart from "./containers/Cart/Cart";
import Home from "./containers/Home/Home";
import Login from "./containers/Login/Login";
import SessionTimeout from "./containers/Login/SessionTimeout";
import ManageCategories from "./containers/ManageCategories/ManageCategories";
import ManageProducts from "./containers/ManageProducts/ManageProducts";
import ManagePromotions from "./containers/ManagePromotions/ManagePromotions";
import Orders from "./containers/Orders/Orders";
import ProductDetails from "./containers/ProductDetails/ProductDetails";
import Layout from "./hoc/Layout/Layout";
import { authService } from "./services";
import { AppDispatch } from "./store";
import { checkAuthenticationState } from "./store/Login/login-slice";

function App() {
  const dispatch = useDispatch<AppDispatch>();

  useEffect(() => {
    dispatch(checkAuthenticationState());
  }, [dispatch]);

  const handleOnIdle = (event?: Event) => {
    console.log("user is idle", event);
    console.log("last active", getLastActiveTime());
    authService.handleTimeout();
  };

  const { getLastActiveTime } = useIdleTimer({
    timeout: 1000 * 60 * 15,
    onIdle: handleOnIdle,
    debounce: 500,
  });

  return (
    <React.Fragment>
      <Layout>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/session-timeout" element={<SessionTimeout />} />
          <Route path="/cart" element={<Cart />} />
          <Route path="/product/:id" element={<ProductDetails />} />
          <Route path="/add-Product" element={<AddProduct />} />
          <Route path="/manage-products" element={<ManageProducts />} />
          <Route path="/manage-categories" element={<ManageCategories />} />
          <Route path="/manage-promotions" element={<ManagePromotions />} />
          <Route path="/orders" element={<Orders />} />
          <Route path="/" element={<Home />} />
        </Routes>
      </Layout>
      <ToastContainer />
    </React.Fragment>
  );
}

export default App;
