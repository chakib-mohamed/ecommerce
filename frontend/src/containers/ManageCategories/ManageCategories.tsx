import React, { useEffect, useState } from "react";
import AddCategory from "../../components/AddCategory/AddCategory";
import Categories from "../../components/Categories/Categories";
import { service } from "../../services";
import { Category } from "../../types/types";

const ManageCategories: React.FC = () => {
  const [categories, setCategories] = useState<{ id: string; label: string }[] | null>(null);

  useEffect(() => {
    fetchCategories();
  }, []);

  const reloadCategories = () => {
    fetchCategories();
  };

  const fetchCategories = () => {
    service.fetchCategories().then((data: Category[]) => {
      const formattedCategories = data.map((cat: Category) => ({
        id: cat.value,
        label: cat.name,
      }));
      setCategories(formattedCategories);
    });
  };

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-16 animate-in fade-in slide-in-from-bottom duration-700">
      <div className="text-center space-y-4">
        <h1 className="text-4xl font-black text-slate-900 tracking-tight">Category Management</h1>
        <p className="text-slate-500 font-medium italic">Organize your inventory with precision and style</p>
      </div>

      <div className="max-w-2xl mx-auto space-y-8">
        <section className="bg-white/50 backdrop-blur-xl p-8 rounded-[2.5rem] border border-white/40 shadow-xl space-y-6">
          <div className="flex items-center space-x-3 mb-2">
            <div className="w-8 h-8 bg-blue-600 rounded-xl flex items-center justify-center text-white shadow-lg transition-transform hover:rotate-12">
              <i className="fa fa-plus text-xs"></i>
            </div>
            <h2 className="text-xl font-black text-slate-900 tracking-tight italic">Add New Category</h2>
          </div>
          <AddCategory onCategoryAdded={reloadCategories} />
        </section>

        {categories && categories.length > 0 && (
          <section className="space-y-6">
            <div className="flex items-center justify-between px-4">
              <h2 className="text-sm font-black text-slate-400 uppercase tracking-[0.2em] italic">Active Categories</h2>
              <span className="text-xs font-bold text-blue-600 bg-blue-50 px-3 py-1 rounded-full border border-blue-100 italic">
                {categories.length} Total
              </span>
            </div>
            <div className="bg-white/30 backdrop-blur-md rounded-[2.5rem] border border-white/20 shadow-lg overflow-hidden transition-all hover:shadow-2xl">
              <Categories
                categories={categories}
                onCategoryDeleted={reloadCategories}
              />
            </div>
          </section>
        )}
      </div>
    </main>
  );
};

export default ManageCategories;
