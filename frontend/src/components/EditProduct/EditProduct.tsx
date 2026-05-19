import React, { useEffect } from "react";
import { useForm } from "react-hook-form";
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "../../store";
import * as actions from "../../store/ManageProducts/actions";
import { Category } from "../../types/types";

interface ProductForm {
  id: string;
  title: string;
  category: string;
  description: string;
  image: string;
  price: string | number;
}

const EditProduct: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const { productID, product, categories } = useSelector((state: RootState) => state.manageProducts);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isValid, isSubmitting }
  } = useForm<ProductForm>({
    mode: "onBlur"
  });

  useEffect(() => {
    dispatch(actions.fetchCategories());
  }, [dispatch]);

  useEffect(() => {
    if (product) {
      reset({
        id: productID,
        title: product.title,
        description: product.description,
        price: product.price,
        image: product.image,
        category: product.category
      });
    }
  }, [product, productID, reset]);

  const onSubmit = (data: ProductForm) => {
    dispatch(actions.updateProduct(data as unknown as import("../../types/types").Product));
  };

  if (!product) return null;

  return (
    <div className="space-y-8 animate-in fade-in zoom-in duration-500">
      <div className="flex items-center space-x-4 mb-4">
        <div className="w-12 h-12 bg-blue-50 rounded-2xl flex items-center justify-center text-blue-600 border border-blue-100 shadow-sm">
          <i className="fa fa-edit text-xl"></i>
        </div>
        <div>
          <h2 className="text-2xl font-black text-slate-900 tracking-tight">Modify Product</h2>
          <p className="text-xs font-bold text-slate-400 uppercase tracking-widest italic">Inventory ID: #{productID}</p>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        <div className="space-y-4">
          {/* Title */}
          <div className="space-y-1">
            <label className="block text-xs font-black text-slate-700 ml-1 uppercase tracking-widest italic">Product Title</label>
            <input
              {...register("title", { required: "Title is required" })}
              placeholder="Product name"
              className={`block w-full px-5 py-4 bg-slate-50 border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none
                ${errors.title ? 'border-red-100 focus:border-red-500' : 'border-transparent focus:border-blue-500 shadow-sm'}`}
            />
          </div>

          {/* Description */}
          <div className="space-y-1">
            <label className="block text-xs font-black text-slate-700 ml-1 uppercase tracking-widest italic">Detailed Description</label>
            <textarea
              {...register("description", { 
                required: "Required",
                minLength: { value: 5, message: "Too short" },
                maxLength: { value: 200, message: "Too long" }
              })}
              rows={4}
              className={`block w-full px-5 py-4 bg-slate-50 border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none resize-none
                ${errors.description ? 'border-red-100 focus:border-red-500' : 'border-transparent focus:border-blue-500 shadow-sm'}`}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
            {/* Category */}
            <div className="space-y-1">
              <label className="block text-xs font-black text-slate-700 ml-1 uppercase tracking-widest italic">Category Selection</label>
              <select
                {...register("category", { required: "Required" })}
                className={`block w-full px-5 py-4 bg-slate-50 border-2 rounded-2xl text-slate-900 transition-all outline-none appearance-none cursor-pointer
                  ${errors.category ? 'border-red-100 focus:border-red-500' : 'border-transparent focus:border-blue-500 shadow-sm'}`}
              >
                <option value="">None</option>
                {categories?.map((c: Category) => (
                  <option key={c.value} value={c.value}>{c.name}</option>
                ))}
              </select>
            </div>

            {/* Price */}
            <div className="space-y-1">
              <label className="block text-xs font-black text-slate-700 ml-1 uppercase tracking-widest italic">Base Price ($)</label>
              <input
                {...register("price", { 
                  required: "Required",
                  pattern: { value: /^\d+(\.\d{1,2})?$/, message: "Invalid price" }
                })}
                className={`block w-full px-5 py-4 bg-slate-50 border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none tabular-nums
                  ${errors.price ? 'border-red-100 focus:border-red-500' : 'border-transparent focus:border-blue-500 shadow-sm'}`}
              />
            </div>
          </div>

          {/* Image URL */}
          <div className="space-y-1">
            <label className="block text-xs font-black text-slate-700 ml-1 uppercase tracking-widest italic">Asset Reference (Image)</label>
            <input
              {...register("image")}
              placeholder="image_filename.jpg"
              className="block w-full px-5 py-4 bg-slate-50 border-2 border-transparent focus:border-blue-500 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none shadow-sm"
            />
          </div>
        </div>

        <div className="pt-4">
          <button
            type="submit"
            disabled={!isValid || isSubmitting}
            className={`w-full py-5 rounded-2xl text-white font-black text-lg shadow-xl transform transition-all duration-300 flex items-center justify-center space-x-3
              ${!isValid || isSubmitting 
                ? 'bg-slate-300 cursor-not-allowed border-0' 
                : 'bg-blue-600 hover:bg-blue-700 hover:shadow-blue-500/30 active:scale-[0.98] cursor-pointer shadow-blue-500/20 border-0'}`}
          >
            {isSubmitting ? (
              <div className="w-6 h-6 border-3 border-white/30 border-t-white rounded-full animate-spin"></div>
            ) : (
              <>
                <i className="fa fa-save"></i>
                <span>Update Specifications</span>
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
};

export default EditProduct;
