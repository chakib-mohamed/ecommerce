import { format, parseISO } from "date-fns";
import React, { useState } from "react";
import Modal from "../../hoc/Modal/Modal";
import { service } from "../../services";

interface Promotion {
  id: string;
  label: string;
  product?: {
    title: string;
  };
  percentageOff: number | string;
  activeFrom: string;
  activeTo: string;
}

interface PromotionsProps {
  promotions: Promotion[];
  onPromotionDeleted: () => void;
}

const Promotions: React.FC<PromotionsProps> = ({ promotions, onPromotionDeleted }) => {
  const [displayDeleteModal, setDisplayDeleteModal] = useState(false);
  const [selectedPromotionID, setSelectedPromotionID] = useState<string | null>(null);

  const handleDeletePromotion = async () => {
    if (selectedPromotionID) {
      try {
        await service.deletePromotion(selectedPromotionID);
        setDisplayDeleteModal(false);
        onPromotionDeleted();
      } catch (err) {
        console.error("Failed to delete promotion", err);
      }
    }
  };

  const openDeleteModal = (id: string) => {
    setSelectedPromotionID(id);
    setDisplayDeleteModal(true);
  };

  const formatDateString = (dateStr: string) => {
    try {
      return format(parseISO(dateStr), "MMM dd, yyyy");
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="animate-in fade-in duration-700">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-slate-50/50 border-b border-slate-100/50">
              <th className="px-6 py-5 text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] italic">Campaign Label</th>
              <th className="px-6 py-5 text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] italic">Product</th>
              <th className="px-6 py-5 text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] italic text-center">Discount</th>
              <th className="px-6 py-5 text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] italic">Duration</th>
              <th className="px-6 py-5 text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] italic text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100/50">
            {promotions.map((promotion) => (
              <tr key={promotion.id} className="group hover:bg-slate-50/30 transition-colors duration-300">
                <td className="px-6 py-6">
                  <span className="text-sm font-bold text-slate-900 group-hover:text-indigo-600 transition-colors">
                    {promotion.label}
                  </span>
                </td>
                <td className="px-6 py-6">
                  <span className="inline-flex px-3 py-1 bg-slate-100 text-slate-600 rounded-lg text-[10px] font-black uppercase tracking-widest border border-slate-200">
                    {promotion?.product?.title || 'Global'}
                  </span>
                </td>
                <td className="px-6 py-6 text-center">
                  <span className="text-lg font-black text-red-500 italic tracking-tighter">
                    -{promotion.percentageOff}%
                  </span>
                </td>
                <td className="px-6 py-6">
                  <div className="flex flex-col space-y-1">
                    <span className="text-xs font-bold text-slate-700 flex items-center">
                      <i className="fa fa-calendar-alt mr-2 text-[10px] text-slate-300"></i>
                      {formatDateString(promotion.activeFrom)}
                    </span>
                    <i className="fa fa-arrow-down text-[8px] text-slate-200 ml-1"></i>
                    <span className="text-xs font-bold text-slate-400 flex items-center">
                      <i className="fa fa-calendar-check mr-2 text-[10px] text-slate-300"></i>
                      {formatDateString(promotion.activeTo)}
                    </span>
                  </div>
                </td>
                <td className="px-6 py-6 text-right">
                  <button
                    onClick={() => openDeleteModal(promotion.id)}
                    className="p-2.5 bg-white text-slate-300 hover:text-red-500 hover:bg-red-50 rounded-xl border border-slate-100 hover:border-red-100 shadow-sm transition-all active:scale-95 cursor-pointer"
                    title="Remove Promotion"
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
          submitHandler={handleDeletePromotion}
          className="w-full max-w-sm"
          title="Remove Promotion"
        >
          <div className="p-8 text-center space-y-6">
            <div className="inline-flex items-center justify-center p-4 bg-red-50 rounded-full text-red-500 mb-2">
              <i className="fa fa-trash-alt text-2xl"></i>
            </div>
            <div className="space-y-2">
              <h3 className="text-xl font-black text-slate-900 tracking-tight">End Campaign?</h3>
              <p className="text-slate-500 font-medium text-sm">
                This action will permanently end this promotion. Prices will revert to their original values.
              </p>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};

export default Promotions;
