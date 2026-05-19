import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { restApi } from "../../axios-instance";
import Spinner from "../../components/UI/Spinner/Spinner";
import { service } from "../../services";
import { Product } from "../../types/types";

const ProductDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [quantity, setQuantity] = useState(1);
  const [isAdding, setIsAdding] = useState(false);

  useEffect(() => {
    if (id) {
      setLoading(true);
      service.getProduct(id)
        .then((data) => {
          setProduct(data);
          setLoading(false);
        })
        .catch(() => setLoading(false));
    }
  }, [id]);

  const handleAddToCart = (e: React.FormEvent) => {
    e.preventDefault();
    if (!product) return;

    setIsAdding(true);
    // Mimicking the original direct localStorage manipulation
    const cartStr = localStorage.getItem("CART");
    const cart = cartStr ? JSON.parse(cartStr) : {};
    
    if (cart[product.id]) {
      cart[product.id] = parseInt(cart[product.id]) + quantity;
    } else {
      cart[product.id] = quantity;
    }

    localStorage.setItem("CART", JSON.stringify(cart));
    
    // Smooth transition to cart
    setTimeout(() => {
      navigate("/cart");
    }, 600);
  };

  const incrementQty = () => setQuantity(prev => prev + 1);
  const decrementQty = () => setQuantity(prev => (prev > 1 ? prev - 1 : 1));

  if (loading) return <Spinner loading={true} />;
  if (!product) return (
    <div className="max-w-7xl mx-auto px-4 py-20 text-center">
      <h2 className="text-2xl font-bold text-slate-900">Product not found</h2>
    </div>
  );

  const hasPromotion = product.promotions && product.promotions.length > 0;
  const currentPrice = hasPromotion 
    ? product.price * (1 - product.promotions[0].percentageOff / 100) 
    : product.price;

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 md:py-20 animate-in fade-in slide-in-from-bottom duration-700">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-20 items-start">
        
        {/* Left: Product Image */}
        <div className="lg:col-span-7 space-y-6">
          <div className="relative aspect-square sm:aspect-video lg:aspect-square bg-slate-50 rounded-[3rem] overflow-hidden border border-slate-100 shadow-2xl group">
            <img
              src={restApi.defaults.baseURL + "products/images/" + product.image}
              className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
              alt={product.title}
            />
            {hasPromotion && (
              <div className="absolute top-8 left-8 bg-red-500 text-white font-black px-6 py-2 rounded-full shadow-lg shadow-red-500/30 -rotate-12 italic tracking-widest text-sm uppercase">
                Save {product.promotions[0].percentageOff}%
              </div>
            )}
          </div>
        </div>

        {/* Right: Product Info */}
        <div className="lg:col-span-5 space-y-8 sticky top-24">
          <div className="space-y-4">
            <div className="inline-flex items-center px-4 py-1.5 rounded-full bg-blue-50 text-blue-600 border border-blue-100 text-xs font-black uppercase tracking-widest">
              Available Now
            </div>
            
            <h1 className="text-4xl md:text-5xl font-black text-slate-900 leading-tight tracking-tight">
              {product.title}
            </h1>

            <div className="flex items-center space-x-4">
              <span className="text-4xl font-black text-blue-600 italic tracking-tighter">
                {currentPrice.toFixed(2)} $
              </span>
              {hasPromotion && (
                <span className="text-xl font-medium text-slate-400 line-through">
                  {product.price.toFixed(2)} $
                </span>
              )}
            </div>
          </div>

          <div className="h-px bg-slate-100"></div>

          <div className="space-y-4">
            <h3 className="text-sm font-black text-slate-900 uppercase tracking-widest italic">Description</h3>
            <p className="text-slate-600 leading-relaxed font-medium text-lg">
              {product.description || "No description available for this premium item. Experience the quality and craftsmanship that defines our curated collection."}
            </p>
          </div>

          <div className="space-y-6 pt-4">
            <div className="flex flex-col sm:flex-row items-center space-y-4 sm:space-y-0 sm:space-x-4">
              {/* Qty Selector */}
              <div className="flex items-center bg-slate-100 p-1 rounded-2xl border border-slate-200 w-full sm:w-auto">
                <button 
                  onClick={decrementQty}
                  className="w-12 h-12 flex items-center justify-center rounded-xl bg-white shadow-sm text-slate-600 hover:text-blue-600 active:scale-95 transition-all border-0 cursor-pointer"
                >
                  <i className="fa fa-minus"></i>
                </button>
                <div className="w-16 text-center font-black text-slate-900 text-xl">
                  {quantity}
                </div>
                <button 
                  onClick={incrementQty}
                  className="w-12 h-12 flex items-center justify-center rounded-xl bg-white shadow-sm text-slate-600 hover:text-blue-600 active:scale-95 transition-all border-0 cursor-pointer"
                >
                  <i className="fa fa-plus"></i>
                </button>
              </div>

              {/* Add Button */}
              <button
                onClick={handleAddToCart}
                disabled={isAdding}
                className={`flex-grow w-full sm:w-auto py-5 px-8 rounded-2xl text-white font-black text-lg shadow-xl transform transition-all duration-500 flex items-center justify-center space-x-3 overflow-hidden relative
                  ${isAdding 
                    ? 'bg-green-500 shadow-green-500/30' 
                    : 'bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 hover:shadow-blue-500/40 active:scale-[0.98] cursor-pointer shadow-blue-500/30 border-0'}`}
              >
                {isAdding ? (
                  <>
                    <i className="fa fa-check text-xl animate-bounce"></i>
                    <span>Added to Cart</span>
                  </>
                ) : (
                  <>
                    <i className="fa fa-shopping-bag"></i>
                    <span>Add to Bag</span>
                  </>
                )}
              </button>
            </div>

            <p className="text-xs text-slate-400 font-bold italic flex items-center justify-center sm:justify-start">
              <i className="fa fa-truck mr-2"></i>
              Complementary premium shipping on all orders
            </p>
          </div>

          <div className="h-px bg-slate-100"></div>

          <div className="flex items-center justify-between">
            <div className="flex flex-col">
              <span className="text-[10px] uppercase font-black tracking-widest text-slate-400 mb-1">Product ID</span>
              <span className="text-sm font-bold text-slate-900 tabular-nums">#{product.id}</span>
            </div>
            <div className="flex items-center space-x-4 grayscale opacity-40">
              <i className="fa fa-cc-visa text-xl"></i>
              <i className="fa fa-cc-mastercard text-xl"></i>
              <i className="fa fa-lock text-sm"></i>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
};

export default ProductDetails;
