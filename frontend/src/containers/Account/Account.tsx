import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import Button from "../../components/UI/Button/Button";
import Icon from "../../components/UI/Icon/Icon";
import type { AppDispatch, RootState } from "../../store";
import { logout } from "../../store/Login/login-slice";

const WRAP = "max-w-[760px] mx-auto px-6 pt-10 pb-20";

/** Account view — greeting, recent orders, back-office entry and sign-out. */
const Account: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  const user = useSelector((state: RootState) => state.login.user);

  const isAuthed = typeof user === "object" && user !== null;
  const email = isAuthed ? user.email : undefined;
  const name = email ? email.split("@")[0] : "Friend";

  if (!isAuthed) {
    return (
      <div className={`${WRAP} text-center`}>
        <h1 className="display text-[40px] mb-3">Your account</h1>
        <p className="text-muted mb-6">Log in to see your orders and manage your shop.</p>
        <Button variant="primary" size="lg" onClick={() => navigate("/login")}>
          Log in
        </Button>
      </div>
    );
  }

  return (
    <div className={WRAP}>
      <span className="eyebrow reveal">Account</span>
      <h1 className="display text-[46px] mt-2.5 mb-1.5 reveal" style={{ animationDelay: "60ms" }}>
        Hello, {name}
      </h1>
      <p className="text-muted reveal" style={{ animationDelay: "100ms" }}>
        {email}
      </p>

      <div
        className="rounded-md bg-surface border border-line p-6 mt-6 reveal"
        style={{ animationDelay: "140ms" }}
      >
        <h3 className="font-serif text-[22px] mt-0">Recent orders</h3>
        <div className="text-center py-7 text-muted">
          <p className="mb-3.5">No orders yet — start exploring the shop.</p>
          <Button variant="ghost" onClick={() => navigate("/browse")}>
            Start shopping
          </Button>
        </div>
      </div>

      <div
        className="rounded-md bg-surface border border-line p-6 mt-[18px] reveal"
        style={{ animationDelay: "180ms" }}
      >
        <div className="flex items-center justify-between gap-4">
          <div>
            <h3 className="font-serif text-xl m-0">Run the shop</h3>
            <p className="text-muted text-sm mt-1 mb-0">
              Open the back-office to manage products &amp; orders.
            </p>
          </div>
          <Button variant="primary" onClick={() => navigate("/manage-products")}>
            Back-office <Icon name="arrow" size={16} />
          </Button>
        </div>
      </div>

      <button
        className="mt-[22px] text-[13px] text-muted hover:text-ink bg-transparent border-0 cursor-pointer"
        onClick={() => dispatch(logout())}
      >
        Sign out
      </button>
    </div>
  );
};

export default Account;
