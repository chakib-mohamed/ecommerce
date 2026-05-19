import React, { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import Guard from "../../hoc/Guard/Guard";
import { service } from "../../services";

interface ProductForm {
  title: string;
  category: string;
  description: string;
  image: string;
  price: string;
}

const AddProduct: React.FC = () => {
  const [categories, setCategories] = useState<{ value: string; name: string }[]>([]);
  const [success, setSuccess] = useState(false);
  
  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isValid, isSubmitting }
  } = useForm<ProductForm>({
    mode: "onBlur"
  });

  const watchedValues = watch();

  useEffect(() => {
    service.fetchCategories()
      .then((data) => setCategories(data))
      .catch((err) => console.error("Failed to fetch categories", err));
  }, []);

  const onSubmit = async (data: ProductForm) => {
    try {
      const response = await service.createProduct(data);
      if (response.status === 201 || response.status === 200) {
        setSuccess(true);
        reset();
        setTimeout(() => setSuccess(false), 5000);
      }
    } catch (err) {
      console.error("Failed to create product", err);
    }
  };

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="text-center mb-12 space-y-4">
        <h1 className="text-4xl font-black text-slate-900 tracking-tight">Create New Product</h1>
        <p className="text-slate-500 font-medium italic">Expand your collection with a new premium addition</p>
      </div>

      {success && (
        <div className="max-w-3xl mx-auto mb-8 p-4 bg-green-50 border border-green-100 rounded-2xl flex items-center justify-between animate-in fade-in slide-in-from-top duration-500">
          <div className="flex items-center space-x-3">
            <div className="bg-green-500 p-2 rounded-full text-white shadow-lg shadow-green-200">
              <i className="fa fa-check"></i>
            </div>
            <p className="text-green-800 font-bold">Success! Product has been cataloged.</p>
          </div>
          <button onClick={() => setSuccess(false)} className="text-green-500 hover:text-green-700 bg-transparent border-0 cursor-pointer">
            <i className="fa fa-times"></i>
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-start max-w-6xl mx-auto">
        {/* Form Column */}
        <div className="bg-white/50 backdrop-blur-xl p-8 sm:p-10 rounded-[2.5rem] border border-white/40 shadow-2xl space-y-8 animate-in fade-in slide-in-from-left duration-700">
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
            <div className="space-y-4">
              {/* Title */}
              <div className="space-y-1">
                <label className="block text-sm font-black text-slate-700 ml-1 uppercase tracking-widest italic">Product Title</label>
                <input
                  {...register("title", { required: "Title is required" })}
                  placeholder="e.g. Minimalist Watch"
                  className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none
                    ${errors.title ? 'border-red-100 focus:border-red-500 bg-red-50/10' : 'border-transparent focus:border-blue-500 shadow-sm focus:shadow-blue-100'}`}
                />
              </div>

              {/* Description */}
              <div className="space-y-1">
                <label className="block text-sm font-black text-slate-700 ml-1 uppercase tracking-widest italic">Description</label>
                <textarea
                  {...register("description", { 
                    required: "Required",
                    minLength: { value: 5, message: "Too short" },
                    maxLength: { value: 200, message: "Too long" }
                  })}
                  rows={4}
                  placeholder="Tell the story of this product..."
                  className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none resize-none
                    ${errors.description ? 'border-red-100 focus:border-red-500 bg-red-50/10' : 'border-transparent focus:border-blue-500 shadow-sm focus:shadow-blue-100'}`}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                {/* Category */}
                <div className="space-y-1">
                  <label className="block text-sm font-black text-slate-700 ml-1 uppercase tracking-widest italic">Category</label>
                  <select
                    {...register("category", { required: "Required" })}
                    className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 transition-all outline-none appearance-none cursor-pointer
                      ${errors.category ? 'border-red-100 focus:border-red-500 bg-red-50/10' : 'border-transparent focus:border-blue-500 shadow-sm focus:shadow-blue-100'}`}
                  >
                    <option value="">Select Category</option>
                    {categories.map((c) => (
                      <option key={c.value} value={c.value}>{c.name}</option>
                    ))}
                  </select>
                </div>

                {/* Price */}
                <div className="space-y-1">
                  <label className="block text-sm font-black text-slate-700 ml-1 uppercase tracking-widest italic">Price ($)</label>
                  <input
                    {...register("price", { 
                      required: "Required",
                      pattern: { value: /^\d+(\.\d{1,2})?$/, message: "Invalid price" }
                    })}
                    placeholder="0.00"
                    className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none tabular-nums
                      ${errors.price ? 'border-red-100 focus:border-red-500 bg-red-50/10' : 'border-transparent focus:border-blue-500 shadow-sm focus:shadow-blue-100'}`}
                  />
                </div>
              </div>

              {/* Image URL */}
              <div className="space-y-1">
                <label className="block text-sm font-black text-slate-700 ml-1 uppercase tracking-widest italic">Image Reference</label>
                <div className="relative group">
                  <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
                    <i className="fa fa-image text-sm"></i>
                  </div>
                  <input
                    {...register("image")}
                    placeholder="product_image.jpg"
                    className="block w-full pl-11 pr-5 py-4 bg-white border-2 border-transparent focus:border-blue-500 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none shadow-sm focus:shadow-blue-100"
                  />
                </div>
              </div>
            </div>

            <button
              type="submit"
              disabled={!isValid || isSubmitting}
              className={`w-full py-5 rounded-[1.5rem] text-white font-black text-lg shadow-xl transform transition-all duration-300 flex items-center justify-center space-x-3
                ${!isValid || isSubmitting 
                  ? 'bg-slate-300 cursor-not-allowed border-0' 
                  : 'bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 hover:shadow-blue-500/30 active:scale-[0.98] cursor-pointer shadow-blue-500/20 border-0'}`}
            >
              {isSubmitting ? (
                <div className="w-6 h-6 border-3 border-white/30 border-t-white rounded-full animate-spin"></div>
              ) : (
                <>
                  <i className="fa fa-plus-circle"></i>
                  <span>Create Product</span>
                </>
              )}
            </button>
          </form>
        </div>

        {/* Live Preview Column */}
        <div className="hidden lg:block space-y-6 sticky top-24 animate-in fade-in slide-in-from-right duration-700">
          <h2 className="text-sm font-black text-slate-400 uppercase tracking-[0.2em] ml-2 italic">Live Preview</h2>
          <div className="bg-white p-6 rounded-[2.5rem] border border-slate-100 shadow-2xl overflow-hidden group">
            <div className="aspect-square bg-slate-50 rounded-3xl overflow-hidden mb-6 border border-slate-100 relative group-hover:scale-[1.02] transition-transform duration-500">
              <div className="absolute inset-0 flex items-center justify-center text-slate-200">
                <i className="fa fa-image text-8xl"></i>
              </div>
              <div className="absolute top-4 left-4 inline-flex px-3 py-1 bg-blue-600 text-white text-[10px] font-black uppercase tracking-widest rounded-full shadow-lg">New Item</div>
            </div>
            <div className="space-y-3">
              <div className="h-6 w-32 bg-slate-100 rounded-full animate-pulse transition-all">
                {watchedValues.category && <span className="bg-blue-50 text-blue-600 px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-widest">{watchedValues.category}</span>}
              </div>
              <h3 className="text-2xl font-black text-slate-900 group-hover:text-blue-600 transition-colors">
                {watchedValues.title || "Product Title"}
              </h3>
              <p className="text-slate-500 text-sm font-medium line-clamp-2 italic h-10">
                {watchedValues.description || "Start typing to see your description here..."}
              </p>
              <div className="flex items-center justify-between pt-4 border-t border-slate-50">
                <span className="text-2xl font-black text-slate-900 italic tracking-tighter">
                  {watchedValues.price ? `${parseFloat(watchedValues.price).toFixed(2)} $` : "0.00 $"}
                </span>
                <div className="w-10 h-10 bg-slate-900 text-white rounded-xl flex items-center justify-center shadow-lg">
                  <i className="fa fa-plus text-sm"></i>
                </div>
              </div>
            </div>
          </div>
          
          <div className="bg-indigo-50 border border-indigo-100 p-6 rounded-[2rem] space-y-2">
            <div className="flex items-center text-indigo-600 space-x-2 mb-2">
              <i className="fa fa-sparkles text-sm"></i>
              <span className="text-xs font-black uppercase tracking-[0.1em]">Admin Tip</span>
            </div>
            <p className="text-indigo-900/70 text-sm font-medium leading-relaxed italic">
              Use high-quality images and compelling descriptions to increase conversion rates. Your premium brand deserves elite presentation.
            </p>
          </div>
        </div>
      </div>
    </main>
  );
};

const AddProductPage = Guard(AddProduct);
export default AddProductPage;
