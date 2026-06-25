import React from "react";
import { useNavigate } from "react-router-dom";
import type { Product } from "../../../data/catalog";
import { subName } from "../../../lib/catalog-helpers";
import { money } from "../../../lib/money";
import { useAddToCart } from "../../../lib/use-add-to-cart";
import Button from "../../UI/Button/Button";
import PhotoTile from "../../UI/PhotoTile/PhotoTile";

interface ProductCardProps {
  product: Product;
  /** entrance-stagger delay in ms */
  delay?: number;
}

/** Storefront product card — photo tile, hover-reveal add button, name/price. */
export default function ProductCard({ product: p, delay = 0 }: ProductCardProps) {
  const navigate = useNavigate();
  const addToCart = useAddToCart();

  return (
    <div className="reveal" style={{ animationDelay: delay + "ms" }}>
      <div
        onClick={() => navigate(`/product/${p.id}`)}
        className="group cursor-pointer"
      >
        <div className="relative rounded-md overflow-hidden transition-[transform,box-shadow] duration-300 ease-editorial group-hover:-translate-y-1 group-hover:shadow-pop">
          <PhotoTile tone={p.tone} name={p.name} className="aspect-[4/5] !text-[64px]" />
          {p.badge && (
            <span
              className="absolute top-3 left-3 inline-flex items-center text-[11px] font-bold tracking-[0.04em] uppercase px-[9px] py-[3px] rounded-full text-white"
              style={{ background: p.badge === "New" ? "var(--ink)" : "var(--warn)" }}
            >
              {p.badge}
            </span>
          )}
          <div className="absolute left-3 right-3 bottom-3 opacity-0 translate-y-2 transition-all duration-200 ease-editorial group-hover:opacity-100 group-hover:translate-y-0">
            <Button
              variant="accent"
              size="sm"
              block
              onClick={(e: React.MouseEvent) => {
                e.stopPropagation();
                addToCart(p);
              }}
            >
              Add to cart
            </Button>
          </div>
        </div>
        <div className="pt-3 px-0.5">
          <div className="flex items-start justify-between gap-2.5">
            <span className="text-[15px] font-medium leading-tight">{p.name}</span>
            <span className="price text-[15px] font-semibold whitespace-nowrap shrink-0">
              {money(p.price)}
            </span>
          </div>
          <div className="text-muted text-[13px] mt-[5px]">{subName(p.cat, p.sub)}</div>
        </div>
      </div>
    </div>
  );
}
