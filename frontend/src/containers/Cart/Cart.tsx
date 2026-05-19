import { isAfter, parseISO } from "date-fns";
import React, { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { NavLink } from "react-router-dom";
import { restApi } from "../../axios-instance";
import Checkout from "../../components/Checkout/Checkout";
import Modal from "../../hoc/Modal/Modal";
import { AppDispatch, RootState } from "../../store";
import * as actions from "../../store/Cart/actions";
import { OrderCommand, Product, PromotionType } from "../../types/types";

type CartProduct = Product & { qty: number };

const Cart: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const {
    products,
    isLoading,
    displayCheckoutModal,
    displaySuccessMessage,
  } = useSelector((state: RootState) => state.cart);

  const { user } = useSelector((state: RootState) => state.login);
  const isUserAuthenticated = user && user !== "anonymous";

  useEffect(() => {
    dispatch(actions.loadProducts());
    return () => {
      dispatch(actions.resetState());
    };
  }, [dispatch]);

  const handleUpdateQty = (productID: string, qty: number) => {
    if (qty >= 1) {
      dispatch(actions.updateQty(productID, qty));
    }
  };

  const handleRemove = (productID: string) => {
    dispatch(actions.removeProductFromCart(productID));
  };

  const handleCheckout = (orderCommand: OrderCommand) => {
    const fullCommand: OrderCommand = {
      ...orderCommand,
      products: products.map((p: CartProduct) => ({ productID: p.id, qty: p.qty })),
      userID: (user as { uid: string }).uid,
    };
    dispatch(actions.checkout(fullCommand));
  };

  const promotionIsActive = (promotion: PromotionType) => {
    const currentDate = new Date();
    return (
      isAfter(currentDate, parseISO(promotion.activeFrom)) &&
      isAfter(parseISO(promotion.activeTo), currentDate)
    );
  };

  const getProductPrice = (product: CartProduct) => {
    const activePromotions = product.promotions?.filter((p: PromotionType) => promotionIsActive(p)) || [];
    const discount = activePromotions.reduce((x: number, y: PromotionType) => x + y.percentageOff, 0);
    return product.price * (1 - discount / 100);
  };

  const subtotal = products.reduce((acc: number, p: CartProduct) => {
    return acc + getProductPrice(p) * p.qty;
  }, 0);

  const totalItems = products.reduce((acc: number, p: CartProduct) => acc + p.qty, 0);

  if (products.length === 0 && !isLoading) {
    return (
      <main className="max-w-7xl mx-auto px-4 py-16 sm:px-6 lg:px-8 text-center">
        <div className="space-y-6">
          <div className="inline-flex items-center justify-center p-6 bg-blue-50 rounded-full mb-4">
            <i className="fa fa-shopping-cart text-5xl text-blue-400"></i>
          </div>
          <h2 className="text-3xl font-black text-slate-900 tracking-tight">Your cart is empty</h2>
          <p className="text-slate-500 max-w-sm mx-auto font-medium">
            Looks like you haven't added anything to your cart yet. Explore our products and find something you love!
          </p>
          <NavLink
            to="/"
            className="inline-flex items-center px-8 py-3 rounded-full bg-blue-600 text-white font-bold shadow-lg hover:shadow-blue-500/25 hover:bg-blue-700 transition-all transform hover:-translate-y-0.5 active:translate-y-0"
          >
            Start Shopping
          </NavLink>
        </div>
      </main>
    );
  }

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      {displaySuccessMessage && (
        <div className="mb-8 p-4 bg-green-50 border border-green-100 rounded-2xl flex items-center justify-between animate-in fade-in slide-in-from-top duration-500">
          <div className="flex items-center space-x-3">
            <div className="bg-green-500 p-2 rounded-full text-white">
              <i className="fa fa-check"></i>
            </div>
            <p className="text-green-800 font-bold">Success! Your order has been placed.</p>
          </div>
          <button
            onClick={() => dispatch(actions.closeSuccessMessage())}
            className="text-green-500 hover:text-green-700 bg-transparent border-0 cursor-pointer"
          >
            <i className="fa fa-times"></i>
          </button>
        </div>
      )}

      <h1 className="text-4xl font-black text-slate-900 tracking-tight mb-8">Shopping Cart</h1>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-12">
        {/* Cart Items List */}
        <div className="lg:col-span-8 space-y-6">
          {products.map((product: CartProduct) => {
            const currentPrice = getProductPrice(product);
            const hasDiscount = currentPrice < product.price;

            return (
              <div key={product.id} className="group relative bg-white p-6 rounded-3xl border border-slate-100 shadow-sm hover:shadow-xl transition-all duration-300">
                <div className="flex flex-col sm:flex-row space-y-4 sm:space-y-0 sm:space-x-6">
                  {/* Product Image */}
                  <div className="relative w-full sm:w-32 h-32 aspect-square flex-shrink-0 bg-slate-50 rounded-2xl overflow-hidden">
                    <img
                      src={restApi.defaults.baseURL + "products/images/" + product.image}
                      className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110"
                      alt={product.title}
                    />
                  </div>

                  {/* Product Info */}
                  <div className="flex-grow space-y-2">
                    <div className="flex justify-between items-start">
                      <h3 className="text-lg font-bold text-slate-900 group-hover:text-blue-600 transition-colors">
                        {product.title}
                      </h3>
                      <button
                        onClick={() => handleRemove(product.id)}
                        className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-xl transition-all border-0 bg-transparent cursor-pointer"
                      >
                        <i className="fa fa-trash"></i>
                      </button>
                    </div>

                    <div className="space-y-4">
                      {/* Price Section */}
                      <div className="flex items-center space-x-3">
                        <span className="text-xl font-black text-slate-900 italic">
                          {currentPrice.toFixed(2)} $
                        </span>
                        {hasDiscount && (
                          <span className="text-sm font-medium text-slate-400 line-through">
                            {product.price.toFixed(2)} $
                          </span>
                        )}
                      </div>

                      {/* Quantity Controls */}
                      <div className="flex items-center space-x-4">
                        <div className="flex items-center bg-slate-50 rounded-2xl p-1 border border-slate-100">
                          <button
                            onClick={() => handleUpdateQty(product.id, product.qty - 1)}
                            className="w-8 h-8 flex items-center justify-center rounded-xl bg-white shadow-sm text-slate-600 hover:text-blue-600 active:scale-95 transition-all border-0 cursor-pointer"
                          >
                            <i className="fa fa-minus text-xs"></i>
                          </button>
                          <span className="w-10 text-center font-bold text-slate-900">
                            {product.qty}
                          </span>
                          <button
                            onClick={() => handleUpdateQty(product.id, product.qty + 1)}
                            className="w-8 h-8 flex items-center justify-center rounded-xl bg-white shadow-sm text-slate-600 hover:text-blue-600 active:scale-95 transition-all border-0 cursor-pointer"
                          >
                            <i className="fa fa-plus text-xs"></i>
                          </button>
                        </div>
                        <span className="text-sm font-medium text-slate-500 italic">
                          Total: <span className="text-slate-900 font-bold">{(currentPrice * product.qty).toFixed(2)} $</span>
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Order Summary Sidebar */}
        <div className="lg:col-span-4">
          <div className="sticky top-24 bg-white p-8 rounded-[2.5rem] border border-slate-100 shadow-2xl space-y-6">
            <h2 className="text-2xl font-black text-slate-900 tracking-tight">Order Summary</h2>

            <div className="space-y-4 text-slate-600 font-medium">
              <div className="flex justify-between">
                <span>Items ({totalItems})</span>
                <span className="text-slate-900">{subtotal.toFixed(2)} $</span>
              </div>
              <div className="flex justify-between">
                <span>Shipping</span>
                <span className="text-green-600 font-bold italic">FREE</span>
              </div>
              <div className="flex justify-between">
                <span>Tax</span>
                <span className="text-slate-900">Calculated later</span>
              </div>
              <div className="h-px bg-slate-100 my-4"></div>
              <div className="flex justify-between text-xl font-black tracking-tight">
                <span className="text-slate-900">Est. Total</span>
                <span className="text-blue-600">{subtotal.toFixed(2)} $</span>
              </div>
            </div>

            {isUserAuthenticated ? (
              <button
                onClick={() => dispatch(actions.openCheckouModal())}
                disabled={totalItems === 0}
                className={`w-full py-5 rounded-2xl text-white font-bold text-lg shadow-lg transform transition-all duration-300 flex items-center justify-center space-x-3
                  ${totalItems === 0
                    ? 'bg-slate-300 cursor-not-allowed border-0'
                    : 'bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 hover:shadow-blue-500/25 active:scale-[0.98] border-0 cursor-pointer shadow-blue-500/20'}`}
              >
                <span>Proceed to Checkout</span>
                <i className="fa fa-arrow-right text-sm"></i>
              </button>
            ) : (
              <NavLink
                to="/login"
                className="w-full py-5 rounded-2xl bg-slate-100 text-slate-700 font-bold text-lg hover:bg-slate-200 transition-all flex items-center justify-center space-x-3 border-0"
              >
                <span>Login to Checkout</span>
                <i className="fa fa-sign-in text-sm"></i>
              </NavLink>
            )}

            <div className="flex items-center justify-center space-x-4 opacity-30 grayscale mt-6">
              <i className="fa fa-cc-visa text-2xl"></i>
              <i className="fa fa-cc-mastercard text-2xl"></i>
              <i className="fa fa-cc-paypal text-2xl"></i>
              <i className="fa fa-lock text-xl"></i>
            </div>
          </div>
        </div>
      </div>

      {displayCheckoutModal && (
        <Modal
          displayModal={displayCheckoutModal}
          closeModalHandler={() => dispatch(actions.closeCheckouModal())}
          className="w-full max-w-2xl"
          disableActionSection
          title=" "
        >
          <div className="p-4 sm:p-8">
            <Checkout onCheckout={handleCheckout} />
          </div>
        </Modal>
      )}
    </main>
  );
};

export default Cart;
