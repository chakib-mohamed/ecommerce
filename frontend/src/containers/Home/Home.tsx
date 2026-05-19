import React, { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import Products from "../../components/Products/Products";
import { AppDispatch, RootState } from "../../store";
import { fecthProducts, resetState } from "../../store/Home/home-slice";

const Home: React.FC = () => {
  const products = useSelector((state: RootState) => state.home.products);
  const dispatch = useDispatch<AppDispatch>();

  useEffect(() => {
    dispatch(fecthProducts());

    return () => {
      dispatch(resetState());
    };
  }, [dispatch]);

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="mb-12 flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-black text-slate-900 tracking-tight">
            Discover Our Collection
          </h2>
          <p className="mt-2 text-slate-500 font-medium">
            Premium products curated for your modern lifestyle.
          </p>
        </div>
        <div className="hidden sm:block">
          <span className="inline-flex items-center px-4 py-2 rounded-full bg-blue-50 text-blue-700 text-sm font-bold shadow-sm border border-blue-100 italic">
            {products?.length || 0} Products available
          </span>
        </div>
      </div>

      {products && <Products products={products} />}
    </main>
  );
};

export default Home;
