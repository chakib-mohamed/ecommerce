import React, { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import Button from "../../components/UI/Button/Button";
import { Input } from "../../components/UI/Field/Field";
import Icon from "../../components/UI/Icon/Icon";
import PhotoTile from "../../components/UI/PhotoTile/PhotoTile";
import { cartSubtotal, hydrateCart, shippingFor } from "../../lib/cart";
import { money } from "../../lib/money";
import type { AppDispatch, RootState } from "../../store";
import { clearCart } from "../../store/StoreCart/store-cart-slice";

const WRAP = "max-w-[980px] mx-auto px-6";
const PAY_METHODS = ["Visa", "Mastercard", "Amex", "Apple Pay", "PayPal"];

interface FieldRowProps {
  label: string;
  required?: boolean;
  children: React.ReactNode;
  className?: string;
}
function FieldRow({ label, required, children, className = "" }: FieldRowProps) {
  return (
    <div className={`flex flex-col gap-[7px] ${className}`}>
      <label className="text-[13px] font-semibold text-ink-2">
        {label} {required && <span className="text-accent font-bold">*</span>}
      </label>
      {children}
    </div>
  );
}

interface StepProps {
  n: string;
  title: string;
  icon?: "lock";
  children: React.ReactNode;
}
function Step({ n, title, icon, children }: StepProps) {
  return (
    <div>
      <div className="flex items-center gap-2.5 mb-3.5">
        <span className="w-[26px] h-[26px] rounded-full bg-ink text-paper grid place-items-center text-[13px] font-bold">
          {n}
        </span>
        <h3 className="font-serif text-[22px] m-0">{title}</h3>
        {icon && (
          <span className="text-muted ml-auto">
            <Icon name={icon} size={16} />
          </span>
        )}
      </div>
      <div className="flex flex-col gap-3.5">{children}</div>
    </div>
  );
}

/** Checkout — contact + shipping, then a hand-off to the external payment gateway. */
const Checkout: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  const lines = useSelector((state: RootState) => state.storeCart.items);

  const items = hydrateCart(lines);
  const subtotal = cartSubtotal(lines);
  const shipping = shippingFor(subtotal);
  const total = subtotal + shipping;

  const [f, setF] = useState({ email: "", name: "", addr: "", city: "", zip: "" });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setF({ ...f, [k]: e.target.value });
  const ready = f.email.includes("@") && f.name.trim() !== "" && f.addr.trim() !== "" && f.city.trim() !== "";

  const placeOrder = () => {
    // Production: create a gateway checkout session and redirect to its hosted page.
    dispatch(clearCart());
    navigate("/confirm", { state: { total: money(total) } });
  };

  if (items.length === 0) {
    return (
      <div className={`${WRAP} py-24 text-center`}>
        <h1 className="display text-[40px] mb-3">Your cart is empty</h1>
        <Button variant="primary" size="lg" onClick={() => navigate("/browse")}>
          Start shopping
        </Button>
      </div>
    );
  }

  return (
    <div className={`${WRAP} pt-7 pb-20`}>
      <Button variant="quiet" size="sm" onClick={() => navigate("/")}>
        <Icon name="back" size={16} /> Continue shopping
      </Button>
      <h1 className="display text-[44px] mt-3 mb-7">Checkout</h1>

      <div className="grid grid-cols-1 lg:grid-cols-[1.4fr_1fr] gap-11 items-start">
        <div className="flex flex-col gap-7">
          <Step n="1" title="Contact">
            <FieldRow label="Email" required>
              <Input value={f.email} onChange={set("email")} placeholder="you@email.com" />
            </FieldRow>
          </Step>

          <Step n="2" title="Shipping address">
            <FieldRow label="Full name" required>
              <Input value={f.name} onChange={set("name")} placeholder="Your name" />
            </FieldRow>
            <FieldRow label="Address" required>
              <Input value={f.addr} onChange={set("addr")} placeholder="Street address" />
            </FieldRow>
            <div className="flex gap-3">
              <FieldRow label="City" required className="flex-grow">
                <Input value={f.city} onChange={set("city")} placeholder="City" />
              </FieldRow>
              <FieldRow label="ZIP" className="w-[120px]">
                <Input value={f.zip} onChange={set("zip")} placeholder="ZIP" />
              </FieldRow>
            </div>
          </Step>

          <Step n="3" title="Payment" icon="lock">
            <div
              className="rounded-md p-5 border border-dashed border-line-2"
              style={{ background: "var(--paper-2)" }}
            >
              <div className="flex gap-3 items-start">
                <span className="text-accent shrink-0 mt-0.5">
                  <Icon name="lock" size={18} />
                </span>
                <div>
                  <div className="text-sm font-semibold mb-1.5">Secure payment</div>
                  <p className="text-muted text-[13px] leading-relaxed m-0">
                    You'll be redirected to our payment provider to complete checkout safely. We
                    never store your card details.
                  </p>
                </div>
              </div>
              <div className="flex gap-2.5 mt-4 flex-wrap">
                {PAY_METHODS.map((m) => (
                  <span
                    key={m}
                    className="px-[11px] py-[5px] bg-surface border border-line rounded-full text-xs text-ink-2 font-medium"
                  >
                    {m}
                  </span>
                ))}
              </div>
            </div>
          </Step>

          <Button variant="accent" size="lg" disabled={!ready} onClick={placeOrder}>
            <Icon name="lock" size={16} /> Continue to payment · {money(total)}
          </Button>
        </div>

        {/* summary */}
        <div className="rounded-md bg-surface border border-line p-[22px] lg:sticky lg:top-[88px]">
          <h3 className="font-serif text-xl mt-0 mb-3.5">Order summary</h3>
          <div className="flex flex-col gap-3 mb-3.5">
            {items.map((it) => (
              <div key={it.key} className="flex gap-3 items-center">
                <div className="w-11 h-[52px] rounded-[6px] overflow-hidden shrink-0">
                  <PhotoTile
                    tone={it.product.tone}
                    name={it.product.name}
                    label=""
                    className="w-full h-full !text-[18px]"
                  />
                </div>
                <div className="flex-grow">
                  <div className="text-sm">{it.product.name}</div>
                  <div className="text-muted text-xs capitalize">
                    {it.color} · ×{it.qty}
                  </div>
                </div>
                <span className="price text-sm">{money(it.lineTotal)}</span>
              </div>
            ))}
          </div>
          <hr className="border-0 border-t border-line" />
          <div className="flex items-center justify-between text-sm text-muted mt-3.5 mb-1.5">
            <span>Subtotal</span>
            <span className="price text-ink">{money(subtotal)}</span>
          </div>
          <div className="flex items-center justify-between text-sm text-muted mb-3">
            <span>Shipping</span>
            <span className="text-ink">{shipping === 0 ? "Free" : money(shipping)}</span>
          </div>
          <div className="flex items-center justify-between text-lg font-semibold">
            <span>Total</span>
            <span className="price">{money(total)}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Checkout;
