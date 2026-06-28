import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useLocation, useNavigate } from "react-router-dom";
import { cartCount } from "../../../lib/cart";
import { useCatalogCategories } from "../../../lib/use-catalog";
import type { AppDispatch, RootState } from "../../../store";
import { openDrawer, openSearch } from "../../../store/StoreCart/store-cart-slice";
import Icon from "../../UI/Icon/Icon";
import IconButton from "../../UI/IconButton/IconButton";
import Logo from "../../UI/Logo/Logo";

/** Storefront header — wordmark, category nav (hover dropdowns), search/account/cart. */
export default function Header() {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  const { pathname } = useLocation();
  const [open, setOpen] = useState<string | null>(null);
  const categories = useCatalogCategories();

  const count = useSelector((state: RootState) => cartCount(state.storeCart.items));

  // active category from /browse/:cat
  const match = pathname.match(/^\/browse\/([^/]+)/);
  const activeCat = match ? match[1] : null;

  return (
    <header
      className="sticky top-0 z-50 border-b border-line backdrop-blur-[10px]"
      style={{ background: "color-mix(in oklab, var(--paper) 88%, transparent)" }}
    >
      <div className="max-w-[1180px] mx-auto px-6 h-[68px] flex items-center justify-between">
        <div className="flex items-center gap-7">
          <Logo onClick={() => navigate("/")} />
          <nav className="hidden md:flex items-center gap-1" onMouseLeave={() => setOpen(null)}>
            {categories.map((c) => (
              <div key={c.id} className="relative" onMouseEnter={() => setOpen(c.id)}>
                <button
                  onClick={() => navigate(`/browse/${c.id}`)}
                  className={
                    "bg-transparent border-0 cursor-pointer text-sm font-medium px-3 py-2 rounded-sm " +
                    (activeCat === c.id ? "text-ink" : "text-ink-2 hover:text-ink")
                  }
                >
                  {c.name}
                </button>
                {open === c.id && (
                  <div
                    className="absolute top-full left-0 mt-1 p-2 min-w-[180px] rounded-md bg-surface border border-line reveal"
                    style={{ boxShadow: "var(--shadow-lg)", animationDuration: ".2s" }}
                  >
                    {c.subs.map((s) => (
                      <button
                        key={s.id}
                        onClick={() => navigate(`/browse/${c.id}/${s.id}`)}
                        className="block w-full text-left bg-transparent border-0 cursor-pointer text-sm text-ink-2 px-3 py-2 rounded-sm hover:bg-paper-2"
                      >
                        {s.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </nav>
        </div>

        <div className="flex items-center gap-0.5">
          <IconButton aria-label="Search" onClick={() => dispatch(openSearch())}>
            <Icon name="search" />
          </IconButton>
          <IconButton aria-label="Account" onClick={() => navigate("/account")}>
            <Icon name="user" />
          </IconButton>
          <IconButton aria-label="Cart" onClick={() => dispatch(openDrawer())}>
            <Icon name="cart" />
            {count > 0 && (
              <span className="absolute top-[3px] right-[3px] min-w-[17px] h-[17px] px-1 bg-accent text-white rounded-full text-[10px] font-bold grid place-items-center leading-none">
                {count}
              </span>
            )}
          </IconButton>
        </div>
      </div>
    </header>
  );
}
