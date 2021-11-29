import React from "react";
import { Product } from "../../types/types";
import ProductComponent from "./Product/Product";

type Props = {
  products: Product[];
};

const productsComponent = ({ products }: Props) => {
  const productsList = products.map((product) => (
    <div key={product.id} className="col text-center mb-5">
      <ProductComponent product={product}></ProductComponent>
    </div>
  ));

  return (
    <React.Fragment>
      <div className="row">{productsList}</div>
    </React.Fragment>
  );
};

export default productsComponent;
