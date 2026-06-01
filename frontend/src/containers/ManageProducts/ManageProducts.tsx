import React, { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import EditProduct from "../../components/EditProduct/EditProduct";
import Guard from "../../hoc/Guard/Guard";
import Modal from "../../hoc/Modal/Modal";
import { AppDispatch, RootState } from "../../store";
import * as actions from "../../store/ManageProducts/actions";
import { Product } from "../../types/types";

const ManageProducts: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const { products, displayEditModal, displayDeleteModalConfirmation, productID } = useSelector((state: RootState) => state.manageProducts);

  useEffect(() => {
    dispatch(actions.fetchProducts());
  }, [dispatch]);

  const handleOpenUpdateModal = (id: string) => dispatch(actions.openUpdateProductModal(id));
  const handleCloseUpdateModal = () => dispatch(actions.closeUpdateProductModal());
  const handleOpenDeleteModal = (id: string) => dispatch(actions.openDeleteProductModal(id));
  const handleCloseDeleteModal = () => dispatch(actions.closeDeleteProductModal());
  const handleDeleteProduct = () => dispatch(actions.deleteProduct(productID as string));

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="flex flex-col sm:flex-row justify-between items-center mb-10 space-y-4 sm:space-y-0 text-center sm:text-left">
        <div className="space-y-2">
          <h1 className="text-4xl font-black text-slate-900 tracking-tight">Product Management</h1>
          <p className="text-slate-500 font-medium italic">Overview and management of your digital inventory</p>
        </div>
        <div className="bg-blue-50 px-4 py-2 rounded-2xl border border-blue-100 shadow-sm flex items-center space-x-2">
          <div className="w-2 h-2 bg-blue-500 rounded-full animate-pulse"></div>
          <span className="text-sm font-bold text-blue-700">{products?.length || 0} Products Cataloged</span>
        </div>
      </div>

      <div className="animate-in fade-in slide-in-from-bottom duration-700">
        <div className="overflow-hidden bg-white/50 backdrop-blur-xl rounded-[2.5rem] border border-white/40 shadow-2xl">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50/50 border-b border-slate-100">
                  <th className="px-8 py-6 text-sm font-black text-slate-900 uppercase tracking-widest italic">Product Title</th>
                  <th className="px-8 py-6 text-sm font-black text-slate-900 uppercase tracking-widest italic">Category</th>
                  <th className="px-8 py-6 text-sm font-black text-slate-900 uppercase tracking-widest italic">Price</th>
                  <th className="px-8 py-6 text-sm font-black text-slate-900 uppercase tracking-widest italic text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {products && products.map((product: Product) => (
                  <tr key={product.id} className="group hover:bg-blue-50/30 transition-all duration-300">
                    <td className="px-8 py-8">
                      <div className="flex items-center space-x-4">
                        <div className="w-12 h-12 rounded-xl bg-slate-50 flex items-center justify-center border border-slate-200 shadow-sm group-hover:scale-110 transition-transform overflow-hidden">
                          <img 
                            src={`https://api.dicebear.com/7.x/shapes/svg?seed=${product.title}`} 
                            alt="" 
                            className="w-full h-full object-cover opacity-50"
                          />
                        </div>
                        <span className="text-lg font-bold text-slate-900 group-hover:text-blue-700 transition-colors">
                          {product.title}
                        </span>
                      </div>
                    </td>
                    <td className="px-8 py-8">
                      <span className="inline-flex items-center px-4 py-1 rounded-full bg-slate-100 text-slate-600 border border-slate-200 text-xs font-black uppercase tracking-widest shadow-sm">
                        {product.category || 'General'}
                      </span>
                    </td>
                    <td className="px-8 py-8">
                      <span className="text-xl font-black text-slate-900 italic tracking-tighter">
                        {product.price.toFixed(2)} $
                      </span>
                    </td>
                    <td className="px-8 py-8 text-right">
                      <div className="flex justify-end space-x-3">
                        <button 
                          onClick={() => handleOpenUpdateModal(product.id)}
                          className="p-3 bg-white text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-2xl border border-slate-100 hover:border-blue-100 shadow-sm transition-all active:scale-95 cursor-pointer"
                          title="Edit Product"
                        >
                          <i className="fa fa-edit text-sm"></i>
                        </button>
                        <button 
                          onClick={() => handleOpenDeleteModal(product.id)}
                          className="p-3 bg-white text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-2xl border border-slate-100 hover:border-red-100 shadow-sm transition-all active:scale-95 cursor-pointer"
                          title="Delete Product"
                        >
                          <i className="fa fa-trash text-sm"></i>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {displayEditModal && (
        <Modal
          displayModal={displayEditModal}
          closeModalHandler={handleCloseUpdateModal}
          className="w-full max-w-4xl"
          disableActionSection
          title=" "
        >
          <div className="p-4 sm:p-8">
            <EditProduct />
          </div>
        </Modal>
      )}

      {displayDeleteModalConfirmation && (
        <Modal
          displayModal={displayDeleteModalConfirmation}
          closeModalHandler={handleCloseDeleteModal}
          submitHandler={handleDeleteProduct}
          className="w-full max-w-md"
          title="Delete Product"
        >
          <div className="p-8 text-center space-y-6">
            <div className="inline-flex items-center justify-center p-5 bg-red-50 rounded-full text-red-500 mb-2">
              <i className="fa fa-exclamation-triangle text-3xl animate-bounce"></i>
            </div>
            <div className="space-y-2">
              <h3 className="text-xl font-black text-slate-900 tracking-tight">Confirm Deletion</h3>
              <p className="text-slate-500 font-medium">
                Are you absolutely sure? This action will permanently remove this product from the inventory.
              </p>
            </div>
          </div>
        </Modal>
      )}
    </main>
  );
};

const ManageProductsPage = Guard(ManageProducts);
export default ManageProductsPage;
