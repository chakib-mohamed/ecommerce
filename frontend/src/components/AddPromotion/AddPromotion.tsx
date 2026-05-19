import { isAfter, parseISO } from "date-fns";
import React, { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "react-toastify";
import { service } from "../../services";
import { Product, PromotionType } from "../../types/types";

interface Props {
  onPromotionAdded: () => void;
}

export const AddPromotion: React.FC<Props> = ({ onPromotionAdded }) => {
  const {
    register,
    handleSubmit,
    reset: resetForm,
    formState: { errors, isValid, isSubmitting },
  } = useForm<PromotionType>({
    mode: "onBlur",
  });
  
  const [success, setSuccess] = useState(false);
  const [products, setProducts] = useState<{ name: string; value: string }[]>([]);

  useEffect(() => {
    service.fetchProducts().then((data: Product[]) => {
      const productOptions = data.map((p: Product) => ({
        name: p.title,
        value: p.id,
      }));
      setProducts(productOptions);
    });
  }, []);

  const onSubmitForm = async (promotion: PromotionType) => {
    if (isAfter(parseISO(promotion.activeFrom), parseISO(promotion.activeTo))) {
      toast.warn("The active to date must be greater than active from date");
      return;
    }
    if (isAfter(new Date(), parseISO(promotion.activeTo))) {
      toast.warn("The date of end of the promotion must be in the future");
      return;
    }

    try {
      await service.createPromotion(promotion);
      setSuccess(true);
      resetForm();
      onPromotionAdded();
      setTimeout(() => setSuccess(false), 5000);
    } catch (err) {
      console.error("Failed to create promotion", err);
    }
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      {success && (
        <div className="p-4 bg-green-50 border border-green-100 rounded-2xl flex items-center justify-between mb-4 animate-in slide-in-from-top duration-300">
          <div className="flex items-center space-x-3">
            <div className="bg-green-500 p-2 rounded-full text-white shadow-lg shadow-green-200">
              <i className="fa fa-check"></i>
            </div>
            <p className="text-green-800 font-bold">Promotion successfully initiated!</p>
          </div>
          <button onClick={() => setSuccess(false)} className="text-green-500 hover:text-green-700 bg-transparent border-0 cursor-pointer">
            <i className="fa fa-times"></i>
          </button>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmitForm)} className="space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Label */}
          <div className="space-y-1">
            <label className="block text-xs font-black text-slate-700 ml-1 uppercase tracking-widest italic">Promotion Label</label>
            <input
              {...register("label", { required: "Label is required" })}
              placeholder="e.g. Summer Sale 2026"
              className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none
                ${errors.label ? 'border-red-100 focus:border-red-500 bg-red-50/10' : 'border-transparent focus:border-indigo-500 shadow-sm focus:shadow-indigo-100'}`}
            />
          </div>

          {/* Product Select */}
          <div className="space-y-1">
            <label className="block text-xs font-black text-slate-700 ml-1 uppercase tracking-widest italic">Target Product</label>
            <select
              {...register("product", { required: "Product is required" })}
              className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 transition-all outline-none appearance-none cursor-pointer
                ${errors.product ? 'border-red-100 focus:border-red-500 bg-red-50/10' : 'border-transparent focus:border-indigo-500 shadow-sm focus:shadow-indigo-100'}`}
            >
              <option value="">Select a product</option>
              {products.map((p) => (
                <option key={p.value} value={p.value}>{p.name}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* Active From */}
          <div className="space-y-1">
            <label className="block text-xs font-black text-slate-700 ml-1 uppercase tracking-widest italic">Active From</label>
            <input
              type="date"
              {...register("activeFrom", { required: "Required" })}
              className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 transition-all outline-none
                ${errors.activeFrom ? 'border-red-100 focus:border-red-500 bg-red-50/10' : 'border-transparent focus:border-indigo-500 shadow-sm focus:shadow-indigo-100'}`}
            />
          </div>

          {/* Active To */}
          <div className="space-y-1">
            <label className="block text-xs font-black text-slate-700 ml-1 uppercase tracking-widest italic">Active To</label>
            <input
              type="date"
              {...register("activeTo", { required: "Required" })}
              className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 transition-all outline-none
                ${errors.activeTo ? 'border-red-100 focus:border-red-500 bg-red-50/10' : 'border-transparent focus:border-indigo-500 shadow-sm focus:shadow-indigo-100'}`}
            />
          </div>

          {/* Percentage */}
          <div className="space-y-1">
            <label className="block text-xs font-black text-slate-700 ml-1 uppercase tracking-widest italic">Discount (%)</label>
            <div className="relative">
              <input
                {...register("percentageOff", { 
                  required: "Required", 
                  pattern: { value: /^\d+(\.\d+)?$/, message: "Must be a number" }
                })}
                placeholder="20"
                className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none tabular-nums
                  ${errors.percentageOff ? 'border-red-100 focus:border-red-500 bg-red-50/10' : 'border-transparent focus:border-indigo-500 shadow-sm focus:shadow-indigo-100'}`}
              />
              <div className="absolute right-5 top-1/2 -translate-y-1/2 text-slate-400 font-black">%</div>
            </div>
          </div>
        </div>

        <button
          type="submit"
          disabled={!isValid || isSubmitting}
          className={`w-full py-5 rounded-[1.5rem] text-white font-black text-lg shadow-xl transform transition-all duration-300 flex items-center justify-center space-x-3
            ${!isValid || isSubmitting 
              ? 'bg-slate-300 cursor-not-allowed border-0' 
              : 'bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-700 hover:to-violet-700 hover:shadow-indigo-500/30 active:scale-[0.98] cursor-pointer shadow-indigo-500/20 border-0'}`}
        >
          {isSubmitting ? (
            <div className="w-6 h-6 border-3 border-white/30 border-t-white rounded-full animate-spin"></div>
          ) : (
            <>
              <i className="fa fa-sparkles"></i>
              <span>Initiate Campaign</span>
            </>
          )}
        </button>
      </form>
    </div>
  );
};

export default AddPromotion;
