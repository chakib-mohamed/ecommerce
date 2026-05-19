import React, { useState } from "react";
import { useForm } from "react-hook-form";
import { service } from "../../services";

interface AddCategoryProps {
  onCategoryAdded: () => void;
}

interface CategoryForm {
  label: string;
}

export const AddCategory: React.FC<AddCategoryProps> = ({ onCategoryAdded }) => {
  const [success, setSuccess] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isValid, isSubmitting }
  } = useForm<CategoryForm>({
    mode: "onBlur"
  });

  const onSubmit = async (data: CategoryForm) => {
    try {
      await service.createCategory(data);
      setSuccess(true);
      reset();
      onCategoryAdded();
      setTimeout(() => setSuccess(false), 3000);
    } catch (err) {
      console.error("Failed to create category", err);
    }
  };

  return (
    <div className="space-y-6">
      {success && (
        <div className="p-4 bg-green-50 border border-green-100 rounded-2xl flex items-center justify-between animate-in fade-in slide-in-from-top duration-500">
          <div className="flex items-center space-x-3">
            <div className="bg-green-500 p-1.5 rounded-full text-white shadow-lg shadow-green-200">
              <i className="fa fa-check text-[10px]"></i>
            </div>
            <p className="text-green-800 text-sm font-bold">Category cataloged!</p>
          </div>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div className="space-y-1">
          <label className="block text-[10px] font-black text-slate-400 uppercase tracking-widest italic ml-1">Category Label</label>
          <div className="flex flex-col sm:flex-row space-y-3 sm:space-y-0 sm:space-x-3">
            <div className="relative flex-grow">
              <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 transition-colors">
                <i className="fa fa-tag text-xs"></i>
              </div>
              <input
                {...register("label", { required: "Required" })}
                placeholder="e.g. Premium Accessories"
                className={`block w-full pl-10 pr-4 py-4 bg-white border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none
                  ${errors.label ? 'border-red-100 focus:border-red-500 bg-red-50/10' : 'border-transparent focus:border-blue-500 shadow-sm focus:shadow-blue-100'}`}
              />
            </div>
            <button
              type="submit"
              disabled={!isValid || isSubmitting}
              className={`px-8 py-4 rounded-2xl text-white font-black text-sm shadow-lg transform transition-all duration-300 flex items-center justify-center space-x-2
                ${!isValid || isSubmitting 
                  ? 'bg-slate-300 cursor-not-allowed border-0' 
                  : 'bg-blue-600 hover:bg-blue-700 hover:shadow-blue-500/30 active:scale-[0.98] cursor-pointer border-0 shadow-blue-500/20'}`}
            >
              {isSubmitting ? (
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
              ) : (
                <>
                  <i className="fa fa-plus-circle"></i>
                  <span>Add Category</span>
                </>
              )}
            </button>
          </div>
          {errors.label && (
            <p className="text-[10px] font-bold text-red-500 ml-1 mt-1 italic uppercase tracking-tighter">
              {errors.label.message}
            </p>
          )}
        </div>
      </form>
    </div>
  );
};

export default AddCategory;
