import React, { useState } from "react";
import Modal from "../../hoc/Modal/Modal";
import { service } from "../../services";

interface Category {
  id: string;
  label: string;
}

interface CategoriesProps {
  categories: Category[];
  onCategoryDeleted: () => void;
}

const Categories: React.FC<CategoriesProps> = ({ categories, onCategoryDeleted }) => {
  const [displayDeleteModal, setDisplayDeleteModal] = useState(false);
  const [selectedCategoryID, setSelectedCategoryID] = useState<string | null>(null);

  const handleDeleteCategory = async () => {
    if (selectedCategoryID) {
      try {
        await service.deleteCategory(selectedCategoryID);
        setDisplayDeleteModal(false);
        onCategoryDeleted();
      } catch (err) {
        console.error("Failed to delete category", err);
      }
    }
  };

  const openDeleteModal = (id: string) => {
    setSelectedCategoryID(id);
    setDisplayDeleteModal(true);
  };

  return (
    <div className="animate-in fade-in duration-700">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-slate-50/50 border-b border-slate-100/50">
              <th className="px-8 py-5 text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] italic">Label</th>
              <th className="px-8 py-5 text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] italic text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100/50">
            {categories.map((category) => (
              <tr key={category.id} className="group hover:bg-slate-50/50 transition-colors duration-300">
                <td className="px-8 py-5">
                  <span className="text-sm font-bold text-slate-900 group-hover:text-blue-600 transition-colors tabular-nums">
                    {category.label}
                  </span>
                </td>
                <td className="px-8 py-5 text-right">
                  <button
                    onClick={() => openDeleteModal(category.id)}
                    className="p-2.5 bg-white text-slate-300 hover:text-red-500 hover:bg-red-50 rounded-xl border border-slate-100 hover:border-red-100 shadow-sm transition-all active:scale-95 cursor-pointer"
                    title="Delete Category"
                  >
                    <i className="fa fa-trash text-xs"></i>
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {displayDeleteModal && (
        <Modal
          displayModal={displayDeleteModal}
          closeModalHandler={() => setDisplayDeleteModal(false)}
          submitHandler={handleDeleteCategory}
          className="w-full max-w-sm"
          title="Delete Category"
        >
          <div className="p-8 text-center space-y-6">
            <div className="inline-flex items-center justify-center p-4 bg-red-50 rounded-full text-red-500 mb-2">
              <i className="fa fa-trash-alt text-2xl"></i>
            </div>
            <div className="space-y-2">
              <h3 className="text-xl font-black text-slate-900 tracking-tight">Remove Category?</h3>
              <p className="text-slate-500 font-medium text-sm">
                This action will permanently remove the category. Products assigned to it may need updating.
              </p>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};

export default Categories;
