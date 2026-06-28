import React, { useEffect } from "react";
import { useIdleTimer } from "react-idle-timer";
import { useDispatch } from "react-redux";
import { Navigate, Route, Routes } from "react-router-dom";
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import Account from "./containers/Account/Account";
import AdminCategories from "./containers/Admin/Categories/AdminCategories";
import Dashboard from "./containers/Admin/Dashboard/Dashboard";
import AdminProducts from "./containers/Admin/Products/AdminProducts";
import AdminProductForm from "./containers/Admin/ProductForm/AdminProductForm";
import Browse from "./containers/Browse/Browse";
import Cart from "./containers/Cart/Cart";
import Checkout from "./containers/Checkout/Checkout";
import Confirm from "./containers/Checkout/Confirm";
import Home from "./containers/Home/Home";
import Login from "./containers/Login/Login";
import SessionTimeout from "./containers/Login/SessionTimeout";
import ManagePromotions from "./containers/ManagePromotions/ManagePromotions";
import Orders from "./containers/Orders/Orders";
import ProductDetails from "./containers/ProductDetails/ProductDetails";
import AdminLayout from "./hoc/AdminLayout/AdminLayout";
import Layout from "./hoc/Layout/Layout";
import { authService } from "./services";
import { AppDispatch } from "./store";
import { loadCatalog } from "./store/Catalog/catalog-slice";
import { checkAuthenticationState } from "./store/Login/login-slice";

function App() {
  const dispatch = useDispatch<AppDispatch>();

  useEffect(() => {
    dispatch(checkAuthenticationState());
    dispatch(loadCatalog());
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
          <Route path="/account" element={<Account />} />
          <Route path="/browse" element={<Browse />} />
          <Route path="/browse/:cat" element={<Browse />} />
          <Route path="/browse/:cat/:sub" element={<Browse />} />
          <Route path="/cart" element={<Cart />} />
          <Route path="/checkout" element={<Checkout />} />
          <Route path="/confirm" element={<Confirm />} />
          <Route path="/product/:id" element={<ProductDetails />} />

          {/* Back-office (mock-data redesign) */}
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<Dashboard />} />
            <Route path="products" element={<AdminProducts />} />
            <Route path="products/new" element={<AdminProductForm />} />
            <Route path="products/:id/edit" element={<AdminProductForm />} />
            <Route path="categories" element={<AdminCategories />} />
          </Route>
          {/* Legacy admin paths → redesigned back-office */}
          <Route path="/manage-products" element={<Navigate to="/admin/products" replace />} />
          <Route path="/manage-categories" element={<Navigate to="/admin/categories" replace />} />
          <Route path="/add-Product" element={<Navigate to="/admin/products/new" replace />} />

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
