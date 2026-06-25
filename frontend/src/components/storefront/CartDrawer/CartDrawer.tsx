import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import { cartSubtotal, hydrateCart, shippingFor } from "../../../lib/cart";
import { money } from "../../../lib/money";
import type { AppDispatch, RootState } from "../../../store";
import {
  closeDrawer,
  removeLine,
  setLineQty,
} from "../../../store/StoreCart/store-cart-slice";
import Button from "../../UI/Button/Button";
import Icon from "../../UI/Icon/Icon";
import IconButton from "../../UI/IconButton/IconButton";
import PhotoTile from "../../UI/PhotoTile/PhotoTile";
import Qty from "../../UI/Qty/Qty";

/** Slide-out cart drawer with line items, totals and a checkout hand-off. */
export default function CartDrawer() {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  const { items: lines, drawerOpen } = useSelector((state: RootState) => state.storeCart);

  const items = hydrateCart(lines);
  const subtotal = cartSubtotal(lines);
  const shipping = shippingFor(subtotal);
  const count = items.reduce((n, i) => n + i.qty, 0);

  const close = () => dispatch(closeDrawer());

  return (
    <>
      <div
        onClick={close}
        className="fixed inset-0 z-[90] transition-opacity duration-300 ease-editorial"
        style={{
          background: "rgba(32,30,26,0.4)",
          opacity: drawerOpen ? 1 : 0,
          pointerEvents: drawerOpen ? "auto" : "none",
        }}
      />
      <aside
        className="fixed top-0 right-0 bottom-0 z-[95] w-[min(420px,92vw)] bg-paper flex flex-col transition-transform duration-300 ease-editorial"
        style={{
          transform: drawerOpen ? "none" : "translateX(100%)",
          boxShadow: "var(--shadow-lg)",
        }}
      >
        <div className="flex items-center justify-between px-[22px] py-5 border-b border-line">
          <h2 className="font-serif text-2xl m-0">
            Your cart {count > 0 && <span className="text-muted">({count})</span>}
          </h2>
          <IconButton onClick={close} aria-label="Close cart">
            <Icon name="close" />
          </IconButton>
        </div>

        {items.length === 0 ? (
          <div className="flex-1 grid place-items-center text-center p-8">
            <div>
              <div className="text-faint mb-3 flex justify-center">
                <Icon name="cart" size={40} />
              </div>
              <p className="text-muted mb-4">Your cart is empty.</p>
              <Button variant="ghost" onClick={close}>
                Keep shopping
              </Button>
            </div>
          </div>
        ) : (
          <>
            <div className="flex-1 overflow-y-auto px-[22px] py-2">
              {items.map((it) => (
                <div
                  key={it.key}
                  className="flex gap-3.5 py-4 border-b border-line items-start"
                >
                  <div className="w-[68px] h-20 rounded-sm overflow-hidden shrink-0">
                    <PhotoTile
                      tone={it.product.tone}
                      name={it.product.name}
                      label=""
                      className="w-full h-full !text-[26px]"
                    />
                  </div>
                  <div className="flex-grow">
                    <div className="flex items-start justify-between">
                      <span className="text-[15px] font-medium">{it.product.name}</span>
                      <IconButton
                        size={26}
                        onClick={() => dispatch(removeLine({ id: it.id, color: it.color }))}
                        aria-label="Remove item"
                      >
                        <Icon name="close" size={15} />
                      </IconButton>
                    </div>
                    <div className="text-muted text-[13px] capitalize mb-2">{it.color}</div>
                    <div className="flex items-center justify-between">
                      <Qty
                        value={it.qty}
                        onChange={(q) =>
                          dispatch(setLineQty({ id: it.id, color: it.color, qty: q }))
                        }
                      />
                      <span className="price font-semibold">{money(it.lineTotal)}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <div className="p-[22px] border-t border-line">
              <div className="flex items-center justify-between text-sm text-muted mb-1.5">
                <span>Subtotal</span>
                <span className="price text-ink">{money(subtotal)}</span>
              </div>
              <div className="flex items-center justify-between text-sm text-muted mb-3">
                <span>Shipping</span>
                <span className="text-ink">{shipping === 0 ? "Free" : money(shipping)}</span>
              </div>
              <div className="flex items-center justify-between text-lg font-semibold mb-4">
                <span>Total</span>
                <span className="price">{money(subtotal + shipping)}</span>
              </div>
              <Button
                variant="primary"
                size="lg"
                block
                onClick={() => {
                  close();
                  navigate("/checkout");
                }}
              >
                Checkout <Icon name="arrow" size={16} />
              </Button>
            </div>
          </>
        )}
      </aside>
    </>
  );
}
