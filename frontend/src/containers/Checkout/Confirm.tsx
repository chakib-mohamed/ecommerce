import { useMemo } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import Button from "../../components/UI/Button/Button";
import Icon from "../../components/UI/Icon/Icon";

/** Order confirmation — shown after the checkout hand-off completes. */
const Confirm: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const total = (location.state as { total?: string } | null)?.total;
  const orderNo = useMemo(() => "CS-" + Math.floor(1000 + Math.random() * 9000), []);

  return (
    <div className="max-w-[560px] mx-auto px-6 py-20 text-center">
      <div
        className="reveal w-16 h-16 rounded-full grid place-items-center mx-auto mb-[22px]"
        style={{ background: "var(--accent-soft)", color: "var(--accent-deep)" }}
      >
        <Icon name="check" size={30} />
      </div>
      <h1 className="display text-[46px] mb-3 reveal" style={{ animationDelay: "60ms" }}>
        Thank you!
      </h1>
      <p className="text-[17px] text-ink-2 reveal" style={{ animationDelay: "120ms" }}>
        Your order <b>{orderNo}</b> is confirmed. We've sent a receipt to your email and you'll get
        tracking when it ships.
      </p>
      {total && (
        <div
          className="rounded-md bg-surface border border-line p-5 my-[26px] reveal"
          style={{ animationDelay: "160ms" }}
        >
          <div className="flex items-center justify-between">
            <span className="text-muted">Order total</span>
            <span className="price font-serif text-[22px]">{total}</span>
          </div>
        </div>
      )}
      <div
        className="flex gap-3 justify-center reveal"
        style={{ animationDelay: "200ms" }}
      >
        <Button variant="primary" size="lg" onClick={() => navigate("/")}>
          Back to shop
        </Button>
        <Button variant="ghost" size="lg" onClick={() => navigate("/account")}>
          View account
        </Button>
      </div>
    </div>
  );
};

export default Confirm;
