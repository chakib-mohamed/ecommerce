import React, { Component } from "react";
import { NavLink } from "react-router-dom";
import classes from "./NavBar.module.css";
import { connect } from "react-redux";

import { logout } from "../../store/Login/login-slice";

class NavBar extends Component {
  state = {
    displaySubMenu: false,
  };

  toggleDropDown = () => {
    this.setState((prevState) => {
      return { displaySubMenu: !prevState.displaySubMenu };
    });
  };

  logout = () => {
    this.props.onLogout();
  };

  render() {
    return (
      <nav className="navbar navbar-expand-lg navbar-light bg-light h5">
        <button
          className="navbar-toggler"
          type="button"
          data-toggle="collapse"
          data-target="#navbarSupportedContent"
          aria-controls="navbarSupportedContent"
          aria-expanded="false"
          aria-label="Toggle navigation"
        >
          <span className="navbar-toggler-icon"></span>
        </button>

        <div className="collapse navbar-collapse" id="navbarSupportedContent">
          <ul className="navbar-nav mx-auto">
            <li className={["nav-item", classes.logo].join(" ")}>
              <a href="/" className="nav-link active">
                <i className="fa fa-cloud fa-2x align-middle"></i>
                <span className="ml-1 mb-3">Cloud Shop</span>
              </a>
            </li>
            <li className="nav-item mr-3 my-auto">
              <NavLink to="/" exact className="nav-link">
                Home <span className="sr-only">(current)</span>
              </NavLink>
            </li>

            {this.props.isUserAuthenticated ? (
              <li className="nav-item mr-3 my-auto">
                <NavLink to="/orders" exact className="nav-link">
                  Orders
                </NavLink>
              </li>
            ) : null}

            <li className="nav-item mr-3 my-auto">
              <NavLink to="/cart" exact className="nav-link">
                <i className="fa fa-shopping-cart"></i>
                <span className="ml-1">Cart</span>
              </NavLink>
            </li>

            {this.props.isUserAuthenticated ? (
              <li
                className={
                  "nav-item dropdown mr-3 my-auto " +
                  (this.state.displaySubMenu ? "show" : "")
                }
              >
                <button
                  className="nav-link dropdown-toggle bg-light border-0"
                  id="navbarDropdown"
                  data-toggle="dropdown"
                  aria-haspopup="true"
                  aria-expanded={this.state.displaySubMenu}
                  onClick={() => this.toggleDropDown()}
                >
                  <i className="fa fa-cog"></i>
                  <span className="ml-1">Settings</span>
                </button>
                <div
                  className={
                    "dropdown-menu " + (this.state.displaySubMenu ? "show" : "")
                  }
                  aria-labelledby="navbarDropdown"
                >
                  <NavLink
                    to="/add-product"
                    exact
                    className="dropdown-item"
                    onClick={() => this.toggleDropDown()}
                  >
                    <i className="fa fa-cog"></i>
                    <span className="ml-1">Add Product</span>
                  </NavLink>
                  <NavLink
                    to="/manage-products"
                    exact
                    className="dropdown-item"
                    onClick={() => this.toggleDropDown()}
                  >
                    <i className="fa fa-cog"></i>
                    <span className="ml-1">Manage Products</span>
                  </NavLink>

                  <NavLink
                    to="/manage-categories"
                    exact
                    className="dropdown-item"
                    onClick={() => this.toggleDropDown()}
                  >
                    <i className="fa fa-cog"></i>
                    <span className="ml-1">Manage Categories</span>
                  </NavLink>

                  <NavLink
                    to="/manage-promotions"
                    exact
                    className="dropdown-item"
                    onClick={() => this.toggleDropDown()}
                  >
                    <i className="fa fa-cog"></i>
                    <span className="ml-1">Manage Promotions</span>
                  </NavLink>
                </div>
              </li>
            ) : null}

            {!this.props.isUserAuthenticated ? (
              <li className="nav-item mr-3 my-auto">
                <NavLink to="/login" exact className="nav-link">
                  <i className="fa fa-sign-in"></i>
                  <span className="ml-1">Login</span>
                </NavLink>
              </li>
            ) : null}

            {this.props.isUserAuthenticated ? (
              <li className="nav-item mr-3 my-auto">
                <button
                  className={[
                    "nav-link bg-light border-0",
                    classes.logout,
                  ].join(" ")}
                  onClick={this.props.onLogout}
                >
                  <i className="fa fa-sign-out"></i>
                  <span className="ml-1">Logout</span>
                </button>
              </li>
            ) : null}
          </ul>
        </div>
      </nav>
    );
  }
}

const mapStateToProps = (state) => {
  return {
    isUserAuthenticated: state.login.user && state.login.user !== "anonymous",
  };
};

const mapDispatchToProps = (dispatch) => {
  return {
    onLogout: () => dispatch(logout()),
  };
};

export default connect(mapStateToProps, mapDispatchToProps)(NavBar);
