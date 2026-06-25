import { useEffect, useRef, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import { products } from "../../../data/catalog";
import { catName } from "../../../lib/catalog-helpers";
import { money } from "../../../lib/money";
import type { AppDispatch, RootState } from "../../../store";
import { closeSearch } from "../../../store/StoreCart/store-cart-slice";
import Icon from "../../UI/Icon/Icon";
import IconButton from "../../UI/IconButton/IconButton";
import PhotoTile from "../../UI/PhotoTile/PhotoTile";

/** Full-screen search overlay with live product filtering. */
export default function SearchOverlay() {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  const open = useSelector((state: RootState) => state.storeCart.searchOpen);
  const [q, setQ] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      const t = setTimeout(() => inputRef.current?.focus(), 50);
      return () => clearTimeout(t);
    }
    setQ("");
  }, [open]);

  if (!open) return null;

  const query = q.trim().toLowerCase();
  const results = query
    ? products
        .filter((p) => (p.name + p.cat + p.sub).toLowerCase().includes(query))
        .slice(0, 6)
    : [];

  const close = () => dispatch(closeSearch());
  const goto = (id: string) => {
    navigate(`/product/${id}`);
    close();
  };

  return (
    <div
      className="fixed inset-0 z-[120]"
      style={{ background: "rgba(32,30,26,0.4)" }}
      onClick={close}
    >
      <div
        className="max-w-[560px] mx-auto mt-16 p-0 overflow-hidden rounded-md bg-surface border border-line reveal"
        style={{ boxShadow: "var(--shadow-lg)", animationDuration: ".2s" }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className={
            "flex items-center gap-3 px-[18px] py-4 " +
            (results.length ? "border-b border-line" : "")
          }
        >
          <Icon name="search" />
          <input
            ref={inputRef}
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search products…"
            className="flex-1 border-0 outline-none bg-transparent text-[17px] text-ink placeholder:text-faint"
          />
          <IconButton onClick={close} aria-label="Close search">
            <Icon name="close" />
          </IconButton>
        </div>
        {results.map((p) => (
          <button
            key={p.id}
            onClick={() => goto(p.id)}
            className="flex items-center gap-3 w-full px-[18px] py-3 border-0 bg-transparent cursor-pointer text-left hover:bg-paper-2"
          >
            <div className="w-10 h-12 rounded-[6px] overflow-hidden">
              <PhotoTile tone={p.tone} name={p.name} label="" className="w-full h-full !text-[16px]" />
            </div>
            <div className="flex-grow">
              <div className="text-[15px]">{p.name}</div>
              <div className="text-muted text-[13px]">{catName(p.cat)}</div>
            </div>
            <span className="price text-muted text-sm">{money(p.price)}</span>
          </button>
        ))}
        {query && results.length === 0 && (
          <div className="text-muted px-[18px] py-5 text-sm">No matches for “{q}”.</div>
        )}
      </div>
    </div>
  );
}
