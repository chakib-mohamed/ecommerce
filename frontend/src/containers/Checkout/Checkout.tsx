import React, { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import Button from "../../components/UI/Button/Button";
import { Input } from "../../components/UI/Field/Field";
import Icon from "../../components/UI/Icon/Icon";
import PhotoTile from "../../components/UI/PhotoTile/PhotoTile";
import { shippingFor, subtotalOf } from "../../lib/cart";
import { money } from "../../lib/money";
import { useHydratedCart } from "../../lib/use-cart";
import { service } from "../../services";
import type { AppDispatch, RootState } from "../../store";
import { clearCart } from "../../store/StoreCart/store-cart-slice";

const WRAP = "max-w-[980px] mx-auto px-6";

/**
 * The methods a buyer can pay with, and the opaque reference sent for each.
 *
 * PLACEHOLDER. These are the payment provider's own *test* references, and they stand in for a
 * step this app does not have yet: real checkout collects the card in a provider-hosted field and
 * gets a single-use reference back, so the details never touch our code. Until that exists these
 * let the order reach the provider without a card number ever existing here - which is the rule
 * that matters - but they are not a way to take real money, and every buyer sends the same one.
 *
 * Replacing this means adding the provider's client library and a publishable key; nothing else
 * here changes, because a reference is all that is ever sent.
 */
const PAY_METHODS = [
  { label: "Visa", reference: "pm_card_visa" },
  { label: "Mastercard", reference: "pm_card_mastercard" },
  { label: "Amex", reference: "pm_card_amex" },
] as const;

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
  const user = useSelector((state: RootState) => state.login.user);

  const { items, loading: cartLoading } = useHydratedCart(lines);
  const subtotal = subtotalOf(items);
  const shipping = shippingFor(subtotal);
  const total = subtotal + shipping;

  const [f, setF] = useState({ email: "", name: "", addr: "", city: "", zip: "" });
  const [payMethod, setPayMethod] = useState<string>("");
  const [placing, setPlacing] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setF({ ...f, [k]: e.target.value });
  // A payment method is required, not optional: committing the order asks for payment in the same
  // step, and the request is rejected without one.
  const ready =
    f.email.includes("@") &&
    f.name.trim() !== "" &&
    f.addr.trim() !== "" &&
    f.city.trim() !== "" &&
    payMethod !== "";

  const isAuthenticated = Boolean(user) && user !== "anonymous";

  const placeOrder = async () => {
    // Placing an order requires a signed-in buyer — the order is tied to the
    // session. Send guests to log in, then bring them back to checkout (the cart
    // is preserved in the store).
    if (!isAuthenticated) {
      navigate("/login", { state: { from: "/checkout" } });
      return;
    }
    setPlacing(true);
    try {
      const order = await service.createOrder({
        products: items.map((it) => ({
          product_id: it.product.id,
          title: it.product.name,
          qty: it.qty,
          price: it.product.price,
        })),
      });
      // Creating the order only prices it; it is not committed and no payment is requested until
      // this call. Anything thrown here leaves the order uncommitted rather than half-paid, so the
      // cart is deliberately not cleared until it succeeds - the buyer keeps what they were buying.
      await service.confirmOrder(order.id, payMethod);
      dispatch(clearCart());
      navigate("/confirm", { state: { total: money(total), orderId: order.id } });
    } catch {
      // The API client surfaces failures via a toast (and bounces to login on 401). Staying put
      // is the honest outcome: nothing was charged, and the confirmation page would claim
      // otherwise. Retrying places a second order rather than committing the first - the order id
      // is not kept for a retry - which is a wart worth closing once this page has somewhere to
      // report a payment outcome.
    } finally {
      setPlacing(false);
    }
  };

  if (items.length === 0 && cartLoading) {
    return <div className={`${WRAP} py-24 text-center text-muted`}>Loading your cart…</div>;
  }

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
                    Your payment is handled by our payment provider. We never see or store your
                    card details.
                  </p>
                </div>
              </div>
              <fieldset className="border-0 p-0 m-0 mt-4">
                <legend className="sr-only">Payment method</legend>
                <div className="flex gap-2.5 flex-wrap">
                  {PAY_METHODS.map((m) => {
                    const selected = payMethod === m.reference;
                    return (
                      <label
                        key={m.reference}
                        className={[
                          "px-[11px] py-[5px] border rounded-full text-xs font-medium cursor-pointer",
                          "inline-flex items-center gap-2 transition-colors",
                          selected
                            ? "bg-accent-soft border-accent text-accent-deep"
                            : "bg-surface border-line text-ink-2",
                        ].join(" ")}
                      >
                        <input
                          type="radio"
                          name="payment-method"
                          value={m.reference}
                          checked={selected}
                          onChange={() => setPayMethod(m.reference)}
                          className="accent-[var(--accent)]"
                        />
                        {m.label}
                      </label>
                    );
                  })}
                </div>
              </fieldset>
            </div>
          </Step>

          <Button variant="accent" size="lg" disabled={!ready || placing} onClick={placeOrder}>
            <Icon name="lock" size={16} />{" "}
            {placing
              ? "Placing order…"
              : isAuthenticated
                ? `Place order · ${money(total)}`
                : "Log in to place order"}
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
                    src={it.product.image}
                    tone={it.product.tone}
                    name={it.product.name}
                    label=""
                    className="w-full h-full !text-[18px]"
                  />
                </div>
                <div className="flex-grow">
                  <div className="text-sm">{it.product.name}</div>
                  <div className="text-muted text-xs">×{it.qty}</div>
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
