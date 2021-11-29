import React, { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import Products from "../../components/Products/Products";
import { fecthProducts, resetState } from "../../store/Home/home-slice";

const Home = () => {
  const products = useSelector((state) => state.home.products);
  const dispatch = useDispatch();

  useEffect(() => {
    dispatch(fecthProducts());

    return () => dispatch(resetState());
  }, [dispatch]);

  return (
    <div className="container">
      <h4 className="mb-5">Products</h4>

      {products && <Products products={products}></Products>}
    </div>
  );
};

export default Home;
