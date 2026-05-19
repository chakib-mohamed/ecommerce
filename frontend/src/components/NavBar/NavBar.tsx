import React, { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { NavLink } from "react-router-dom";
import { AppDispatch, RootState } from "../../store";
import { logout } from "../../store/Login/login-slice";

const NavBar: React.FC = () => {
  const [displaySubMenu, setDisplaySubMenu] = useState(false);
  const dispatch = useDispatch<AppDispatch>();

  const isUserAuthenticated = useSelector((state: RootState) =>
    state.login.user && state.login.user !== "anonymous"
  );

  const toggleDropDown = () => {
    setDisplaySubMenu(!displaySubMenu);
  };

  const handleLogout = () => {
    dispatch(logout());
  };

  return (
    <nav className="sticky top-0 z-50 w-full backdrop-blur-md bg-white/70 border-b border-white/20 shadow-sm transition-all duration-300">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16 items-center">
          {/* Logo Section */}
          <div className="flex-shrink-0 flex items-center">
            <NavLink to="/" className="flex items-center space-x-2 group">
              <div className="p-2 rounded-xl bg-blue-600 text-white shadow-lg group-hover:scale-110 transition-transform duration-300">
                <i className="fa fa-cloud text-xl"></i>
              </div>
              <span className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-blue-600 to-indigo-600">
                Cloud Shop
              </span>
            </NavLink>
          </div>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center space-x-8">
            <NavLink 
              to="/" 
              end 
              className={({ isActive }) => 
                `text-sm font-medium transition-colors duration-200 hover:text-blue-600 ${isActive ? 'text-blue-600' : 'text-slate-600'}`
              }
            >
              Home
            </NavLink>

            {isUserAuthenticated && (
              <NavLink 
                to="/orders" 
                className={({ isActive }) => 
                  `text-sm font-medium transition-colors duration-200 hover:text-blue-600 ${isActive ? 'text-blue-600' : 'text-slate-600'}`
                }
              >
                Orders
              </NavLink>
            )}

            <NavLink 
              to="/cart" 
              className={({ isActive }) => 
                `flex items-center space-x-1 text-sm font-medium transition-colors duration-200 hover:text-blue-600 ${isActive ? 'text-blue-600' : 'text-slate-600'}`
              }
            >
              <i className="fa fa-shopping-cart text-lg"></i>
              <span>Cart</span>
            </NavLink>

            {isUserAuthenticated ? (
              <div className="relative">
                <button
                  onClick={toggleDropDown}
                  className="flex items-center space-x-1 text-sm font-medium text-slate-600 hover:text-blue-600 transition-colors duration-200 bg-transparent border-0 cursor-pointer"
                >
                  <i className="fa fa-cog text-lg"></i>
                  <span>Settings</span>
                  <i className={`fa fa-chevron-down text-[10px] transition-transform duration-200 ${displaySubMenu ? 'rotate-180' : ''}`}></i>
                </button>

                {displaySubMenu && (
                  <div className="absolute right-0 mt-2 w-56 rounded-2xl bg-white/90 backdrop-blur-xl shadow-2xl border border-white/20 py-2 ring-1 ring-black ring-opacity-5 animate-in fade-in slide-in-from-top-2 duration-200">
                    <NavLink
                      to="/add-product"
                      className="flex items-center space-x-3 px-4 py-2.5 text-sm text-slate-700 hover:bg-blue-50 transition-colors"
                      onClick={() => setDisplaySubMenu(false)}
                    >
                      <i className="fa fa-plus-circle text-blue-500"></i>
                      <span>Add Product</span>
                    </NavLink>
                    <NavLink
                      to="/manage-products"
                      className="flex items-center space-x-3 px-4 py-2.5 text-sm text-slate-700 hover:bg-blue-50 transition-colors"
                      onClick={() => setDisplaySubMenu(false)}
                    >
                      <i className="fa fa-tasks text-blue-500"></i>
                      <span>Manage Products</span>
                    </NavLink>
                    <NavLink
                      to="/manage-categories"
                      className="flex items-center space-x-3 px-4 py-2.5 text-sm text-slate-700 hover:bg-blue-50 transition-colors"
                      onClick={() => setDisplaySubMenu(false)}
                    >
                      <i className="fa fa-list text-blue-500"></i>
                      <span>Manage Categories</span>
                    </NavLink>
                    <NavLink
                      to="/manage-promotions"
                      className="flex items-center space-x-3 px-4 py-2.5 text-sm text-slate-700 hover:bg-blue-50 transition-colors"
                      onClick={() => setDisplaySubMenu(false)}
                    >
                      <i className="fa fa-tags text-blue-500"></i>
                      <span>Manage Promotions</span>
                    </NavLink>
                    <div className="h-px bg-slate-100 my-1 mx-4"></div>
                    <button
                      onClick={handleLogout}
                      className="w-full flex items-center space-x-3 px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition-colors border-0 bg-transparent cursor-pointer"
                    >
                      <i className="fa fa-sign-out"></i>
                      <span>Logout</span>
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <NavLink 
                to="/login" 
                className="inline-flex items-center px-6 py-2 border border-transparent text-sm font-medium rounded-full text-white bg-blue-600 hover:bg-blue-700 shadow-md hover:shadow-lg transition-all transform hover:-translate-y-0.5 active:translate-y-0"
              >
                <i className="fa fa-sign-in mr-2"></i>
                Login
              </NavLink>
            )}
          </div>

          {/* Mobile Menu Button */}
          <div className="md:hidden flex items-center">
            <button 
              className="p-2 rounded-lg text-slate-600 hover:bg-slate-100 focus:outline-none border-0 bg-transparent cursor-pointer"
              onClick={() => setDisplaySubMenu(!displaySubMenu)}
            >
              <i className={`fa ${displaySubMenu ? 'fa-times' : 'fa-bars'} text-xl`}></i>
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Menu */}
      {displaySubMenu && (
        <div className="md:hidden bg-white/95 backdrop-blur-xl border-t border-slate-100 px-4 pt-2 pb-6 space-y-1 block animate-in slide-in-from-top duration-300">
          <NavLink
            to="/"
            end
            className="block px-3 py-2 rounded-lg text-base font-medium text-slate-600 hover:bg-blue-50 hover:text-blue-600 transition-colors"
            onClick={() => setDisplaySubMenu(false)}
          >
            Home
          </NavLink>
          {isUserAuthenticated && (
            <NavLink
              to="/orders"
              className="block px-3 py-2 rounded-lg text-base font-medium text-slate-600 hover:bg-blue-50 hover:text-blue-600 transition-colors"
              onClick={() => setDisplaySubMenu(false)}
            >
              Orders
            </NavLink>
          )}
          <NavLink
            to="/cart"
            className="block px-3 py-2 rounded-lg text-base font-medium text-slate-600 hover:bg-blue-50 hover:text-blue-600 transition-colors"
            onClick={() => setDisplaySubMenu(false)}
          >
            Cart
          </NavLink>
          {isUserAuthenticated ? (
            <>
              <div className="h-px bg-slate-100 my-2"></div>
              <NavLink to="/add-product" className="block px-3 py-2 text-slate-600 hover:text-blue-600" onClick={() => setDisplaySubMenu(false)}>Add Product</NavLink>
              <NavLink to="/manage-products" className="block px-3 py-2 text-slate-600 hover:text-blue-600" onClick={() => setDisplaySubMenu(false)}>Manage Products</NavLink>
              <button
                onClick={handleLogout}
                className="w-full text-left block px-3 py-2 text-red-600 hover:bg-red-50 border-0 bg-transparent cursor-pointer"
              >
                Logout
              </button>
            </>
          ) : (
            <NavLink
              to="/login"
              className="block px-3 py-2 rounded-lg text-base font-medium text-blue-600 hover:bg-blue-50"
              onClick={() => setDisplaySubMenu(false)}
            >
              Login
            </NavLink>
          )}
        </div>
      )}
    </nav>
  );
};

export default NavBar;
