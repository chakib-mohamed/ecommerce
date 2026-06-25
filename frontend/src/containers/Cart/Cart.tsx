import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import Button from "../../components/UI/Button/Button";
import Icon from "../../components/UI/Icon/Icon";
import IconButton from "../../components/UI/IconButton/IconButton";
import PhotoTile from "../../components/UI/PhotoTile/PhotoTile";
import Qty from "../../components/UI/Qty/Qty";
import { cartSubtotal, hydrateCart, shippingFor } from "../../lib/cart";
import { money } from "../../lib/money";
import { subName } from "../../lib/catalog-helpers";
import type { AppDispatch, RootState } from "../../store";
import { removeLine, setLineQty } from "../../store/StoreCart/store-cart-slice";

const WRAP = "max-w-[1180px] mx-auto px-6";

const Cart: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  const lines = useSelector((state: RootState) => state.storeCart.items);

  const items = hydrateCart(lines);
  const subtotal = cartSubtotal(lines);
  const shipping = shippingFor(subtotal);
  const count = items.reduce((n, i) => n + i.qty, 0);

  if (items.length === 0) {
    return (
      <div className={`${WRAP} py-24 text-center`}>
        <div className="text-faint mb-4 flex justify-center">
          <Icon name="cart" size={48} />
        </div>
        <h1 className="display text-[40px] mb-3">Your cart is empty</h1>
        <p className="text-muted max-w-sm mx-auto mb-6">
          Find something you love — your cart is waiting.
        </p>
        <Button variant="primary" size="lg" onClick={() => navigate("/browse")}>
          Start shopping
        </Button>
      </div>
    );
  }

  return (
    <div className={`${WRAP} pt-7 pb-20`}>
      <h1 className="display text-[44px] mb-8">Your cart</h1>
      <div className="grid grid-cols-1 lg:grid-cols-[1.5fr_1fr] gap-11 items-start">
        {/* line items */}
        <div>
          {items.map((it) => (
            <div
              key={it.key}
              className="flex gap-4 py-5 border-b border-line items-start first:pt-0"
            >
              <div className="w-[88px] h-[104px] rounded-sm overflow-hidden shrink-0">
                <PhotoTile
                  tone={it.product.tone}
                  name={it.product.name}
                  label=""
                  className="w-full h-full !text-[30px]"
                />
              </div>
              <div className="flex-grow">
                <div className="flex items-start justify-between">
                  <div>
                    <div className="text-base font-medium">{it.product.name}</div>
                    <div className="text-muted text-[13px]">
                      {subName(it.product.cat, it.product.sub)}
                    </div>
                    <div className="text-muted text-[13px] capitalize mt-0.5">{it.color}</div>
                  </div>
                  <IconButton
                    size={30}
                    onClick={() => dispatch(removeLine({ id: it.id, color: it.color }))}
                    aria-label="Remove item"
                  >
                    <Icon name="close" size={16} />
                  </IconButton>
                </div>
                <div className="flex items-center justify-between mt-3">
                  <Qty
                    value={it.qty}
                    onChange={(q) => dispatch(setLineQty({ id: it.id, color: it.color, qty: q }))}
                  />
                  <span className="price font-semibold">{money(it.lineTotal)}</span>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* summary */}
        <div className="rounded-md bg-surface border border-line p-[22px] lg:sticky lg:top-[88px]">
          <h2 className="font-serif text-xl mt-0 mb-3.5">Order summary</h2>
          <div className="flex items-center justify-between text-sm text-muted mb-1.5">
            <span>Items ({count})</span>
            <span className="price text-ink">{money(subtotal)}</span>
          </div>
          <div className="flex items-center justify-between text-sm text-muted mb-3">
            <span>Shipping</span>
            <span className="text-ink">{shipping === 0 ? "Free" : money(shipping)}</span>
          </div>
          <hr className="border-0 border-t border-line my-3" />
          <div className="flex items-center justify-between text-lg font-semibold mb-4">
            <span>Total</span>
            <span className="price">{money(subtotal + shipping)}</span>
          </div>
          <Button variant="primary" size="lg" block onClick={() => navigate("/checkout")}>
            Checkout <Icon name="arrow" size={16} />
          </Button>
          <button
            className="mt-3 mx-auto block text-[13px] text-muted hover:text-ink bg-transparent border-0 cursor-pointer"
            onClick={() => navigate("/browse")}
          >
            Continue shopping
          </button>
        </div>
      </div>
    </div>
  );
};

export default Cart;
