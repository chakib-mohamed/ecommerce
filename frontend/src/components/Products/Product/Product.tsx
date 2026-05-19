import { isAfter, parseISO } from "date-fns";
import { NavLink } from "react-router-dom";
import { restApi } from "../../../axios-instance";
import { Product, PromotionType } from "../../../types/types";

type Props = {
  product: Product;
};

const ProductComponent = ({ product }: Props) => {
  const promotionIsActive = (promotion: PromotionType) => {
    const currentDate = new Date();
    return (
      isAfter(currentDate, parseISO(promotion.activeFrom)) &&
      isAfter(parseISO(promotion.activeTo), currentDate)
    );
  };

  const activePromotions = product.promotions?.filter((p) => promotionIsActive(p)) || [];
  const discountTotal = activePromotions.reduce((x, y) => x + y.percentageOff, 0);
  const discountedPrice = product.price * (1 - discountTotal / 100);

  return (
    <NavLink 
      to={"product/" + product.id} 
      className="group"
    >
      <div className="relative flex flex-col h-full bg-white rounded-3xl border border-slate-100 shadow-sm hover:shadow-xl transition-all duration-500 overflow-hidden transform group-hover:-translate-y-2">
        {discountTotal > 0 && (
          <div className="absolute top-4 left-4 z-10 px-3 py-1 bg-red-500 text-white text-xs font-bold rounded-full shadow-lg animate-pulse">
            -{discountTotal}% OFF
          </div>
        )}

        <div className="relative aspect-square overflow-hidden bg-slate-50">
          <img
            src={restApi.defaults.baseURL + "products/images/" + product.image}
            className="w-full h-full object-cover transform group-hover:scale-110 transition-transform duration-700 ease-in-out"
            alt={product.name}
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500"></div>
        </div>

        <div className="flex flex-col flex-grow p-5 space-y-3">
          <div className="space-y-1">
            <h3 className="text-lg font-bold text-slate-900 line-clamp-1 group-hover:text-blue-600 transition-colors">
              {product.name}
            </h3>
            <p className="text-sm text-slate-500 line-clamp-2 leading-relaxed">
              {product.description}
            </p>
          </div>

          <div className="mt-auto pt-4 flex items-center justify-between border-t border-slate-50">
            <div className="flex flex-col">
              {discountTotal > 0 ? (
                <>
                  <span className="text-xs text-slate-400 line-through decoration-red-400/50">
                    {product.price.toFixed(2)} $
                  </span>
                  <span className="text-xl font-black text-slate-900 tracking-tight">
                    {discountedPrice.toFixed(2)} <span className="text-sm font-medium">$</span>
                  </span>
                </>
              ) : (
                <span className="text-xl font-black text-slate-900 tracking-tight">
                  {product.price.toFixed(2)} <span className="text-sm font-medium">$</span>
                </span>
              )}
            </div>

            <div className="p-3 bg-slate-50 text-slate-400 rounded-2xl group-hover:bg-blue-600 group-hover:text-white transition-all duration-300 shadow-inner">
              <i className="fa fa-arrow-right transform group-hover:translate-x-1 transition-transform"></i>
            </div>
          </div>
        </div>
      </div>
    </NavLink>
  );
};

export default ProductComponent;
