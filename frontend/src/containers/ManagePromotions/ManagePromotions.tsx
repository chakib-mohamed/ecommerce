import React, { useEffect, useState } from "react";
import AddPromotion from "../../components/AddPromotion/AddPromotion";
import Promotions from "../../components/Promotions/Promotions";
import Guard from "../../hoc/Guard/Guard";
import { service } from "../../services";
import { PromotionType } from "../../types/types";

const ManagePromotions: React.FC = () => {
  const [promotions, setPromotions] = useState<PromotionType[] | null>(null);

  useEffect(() => {
    fetchPromotions();
  }, []);

  const reloadPromotions = () => {
    fetchPromotions();
  };

  const fetchPromotions = () => {
    service.fetchPromotions().then((data) => {
      setPromotions(data);
    });
  };

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-16 animate-in fade-in slide-in-from-bottom duration-700">
      <div className="text-center space-y-4">
        <h1 className="text-4xl font-black text-slate-900 tracking-tight">Promotions & Offers</h1>
        <p className="text-slate-500 font-medium italic">Drive engagement with compelling rewards and seasonal discounts</p>
      </div>

      <div className="max-w-3xl mx-auto space-y-12">
        <section className="bg-white/50 backdrop-blur-xl p-8 sm:p-10 rounded-[2.5rem] border border-white/40 shadow-xl space-y-6">
          <div className="flex items-center space-x-3 mb-2">
            <div className="w-8 h-8 bg-indigo-600 rounded-xl flex items-center justify-center text-white shadow-lg transition-transform hover:rotate-12">
              <i className="fa fa-percentage text-xs"></i>
            </div>
            <h2 className="text-xl font-black text-slate-900 tracking-tight italic">Initiate Promotion</h2>
          </div>
          <p className="text-sm text-slate-500 font-medium italic mb-4">Set up temporary or recurring offers for your premium products.</p>
          <AddPromotion onPromotionAdded={reloadPromotions} />
        </section>

        {promotions && promotions.length > 0 && (
          <section className="space-y-6">
            <div className="flex items-center justify-between px-4">
              <h2 className="text-sm font-black text-slate-400 uppercase tracking-[0.2em] italic">Active Campaigns</h2>
              <span className="text-xs font-bold text-indigo-600 bg-indigo-50 px-3 py-1 rounded-full border border-indigo-100 italic">
                {promotions.length} Campaigns
              </span>
            </div>
            <div className="bg-white/30 backdrop-blur-md rounded-[3rem] border border-white/20 shadow-2xl overflow-hidden p-2">
              <Promotions
                promotions={promotions}
                onPromotionDeleted={reloadPromotions}
              />
            </div>
          </section>
        )}
      </div>
    </main>
  );
};

const ManagePromotionsPage = Guard(ManagePromotions);
export default ManagePromotionsPage;
