import React, { useEffect } from "react";
import { useDispatch } from "react-redux";
import { Route, Switch } from "react-router-dom";
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import AddProduct from "./containers/AddProduct/AddProduct";
import Cart from "./containers/Cart/Cart";
import Home from "./containers/Home/Home";
import Login from "./containers/Login/Login";
import ManageCategories from "./containers/ManageCategories/ManageCategories";
import ManageProducts from "./containers/ManageProducts/ManageProducts";
import Orders from "./containers/Orders/Orders";
import ProductDetails from "./containers/ProductDetails/ProductDetails";
import Layout from "./hoc/Layout/Layout";
import { checkAuthenticationState } from "./store/Login/login-slice";
import { ManagePromotions } from "./containers/ManagePromotions/ManagePromotions";
import { useIdleTimer } from "react-idle-timer";
import { authService } from "./services";
import { SessionTimeout } from "./containers/Login/SessionTimeout";

function App() {
  const dispatch = useDispatch();

  useEffect(() => {
    dispatch(checkAuthenticationState());
  }, [dispatch]);

  const handleOnIdle = (event) => {
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
        <Switch>
          <Route path="/login" component={Login} />
          <Route path="/session-timeout" component={SessionTimeout} />
          <Route path="/cart" component={Cart} />
          <Route exact path="/product/:id" component={ProductDetails} />
          <Route path="/add-Product" component={AddProduct} />
          <Route path="/manage-products" component={ManageProducts} />
          <Route path="/manage-categories" component={ManageCategories} />
          <Route path="/manage-promotions" component={ManagePromotions} />
          <Route path="/orders" component={Orders} />
          <Route path="/" exact component={Home} />
        </Switch>
      </Layout>
      <ToastContainer />
    </React.Fragment>
  );
}

export default App;
