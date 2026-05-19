import { Product } from "../../types/types";
import ProductComponent from "./Product/Product";

type Props = {
  products: Product[];
};

const ProductsComponent = ({ products }: Props) => {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-y-12 gap-x-8 pb-20">
      {products.map((product) => (
        <ProductComponent key={product.id} product={product} />
      ))}
    </div>
  );
};

export default ProductsComponent;
