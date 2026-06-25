import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import ProductCard from "../../components/storefront/ProductCard/ProductCard";
import Button from "../../components/UI/Button/Button";
import ColorDots from "../../components/UI/ColorDots/ColorDots";
import Icon from "../../components/UI/Icon/Icon";
import PhotoTile from "../../components/UI/PhotoTile/PhotoTile";
import Qty from "../../components/UI/Qty/Qty";
import Stars from "../../components/UI/Stars/Stars";
import { type Swatch } from "../../data/catalog";
import { money } from "../../lib/money";
import { useAddToCart } from "../../lib/use-add-to-cart";
import { useCatalogProducts, useCatName, useSubName } from "../../lib/use-catalog";

const WRAP = "max-w-[1180px] mx-auto px-6";
const GLYPHS = ["", "◐", "◑", "✦"];

type Tab = "desc" | "ship" | "care";
const TAB_COPY: Record<Tab, (blurb: string) => string> = {
  desc: (b) => b + " Each piece is checked by hand before it leaves the studio.",
  ship: () =>
    "Dispatched within 2 working days. Free carbon-neutral delivery on orders over $75. Returns accepted within 30 days.",
  care: () =>
    "Wipe clean with a soft, damp cloth. Avoid harsh detergents. Re-oil timber surfaces every few months to keep them happy.",
};

const ProductDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const addToCart = useAddToCart();
  const products = useCatalogProducts();
  const catName = useCatName();
  const subName = useSubName();

  const product = products.find((x) => x.id === id) ?? products[0];
  const [color, setColor] = useState<Swatch>(product?.colors[0] ?? "sand");
  const [qty, setQty] = useState(1);
  const [shot, setShot] = useState(0);
  const [tab, setTab] = useState<Tab>("desc");

  useEffect(() => {
    if (!product) return;
    setColor(product.colors[0]);
    setQty(1);
    setShot(0);
    // Reset selections whenever the viewed product changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [product?.id]);

  if (!product) {
    return <div className={`${WRAP} py-24 text-center text-muted`}>Loading product…</div>;
  }

  const related = products.filter((x) => x.cat === product.cat && x.id !== product.id).slice(0, 4);

  return (
    <div>
      <div className={`${WRAP} pt-[22px]`}>
        <Button variant="quiet" size="sm" className="reveal" onClick={() => navigate(`/browse/${product.cat}`)}>
          <Icon name="back" size={16} /> {catName(product.cat)}
        </Button>
      </div>

      <div className={`${WRAP} grid grid-cols-1 lg:grid-cols-2 gap-14 mt-3 items-start`}>
        {/* gallery */}
        <div className="reveal lg:sticky lg:top-[88px]">
          <div className="rounded-lg overflow-hidden">
            <PhotoTile
              src={product.image}
              tone={product.tone}
              name={product.name}
              glyph={GLYPHS[shot]}
              className="aspect-square !text-[100px]"
            />
          </div>
          <div className="flex gap-2.5 mt-3">
            {GLYPHS.map((g, i) => (
              <button
                key={i}
                onClick={() => setShot(i)}
                className="w-[72px] h-[72px] rounded-sm overflow-hidden p-0 cursor-pointer bg-transparent"
                style={{ border: shot === i ? "2px solid var(--ink)" : "1.5px solid var(--line-2)" }}
              >
                <PhotoTile src={product.image} tone={product.tone} label="" glyph={g} className="w-full h-full !text-[26px]" />
              </button>
            ))}
          </div>
        </div>

        {/* info */}
        <div className="reveal" style={{ animationDelay: "80ms" }}>
          <div className="text-muted text-[13px]">{subName(product.cat, product.sub)}</div>
          <h1 className="display text-[46px] mt-1.5 mb-3">{product.name}</h1>
          <div className="flex items-center justify-between mb-[18px]">
            <span className="price font-serif text-3xl">{money(product.price)}</span>
            <Stars value={product.rating} reviews={product.reviews} />
          </div>
          <p className="text-base text-ink-2 leading-relaxed mb-6">{product.blurb}</p>

          <div className="mb-[18px]">
            <label className="block text-[13px] font-semibold text-ink-2 mb-[7px]">
              Colour — <span className="capitalize text-muted">{color}</span>
            </label>
            <ColorDots colors={product.colors} value={color} onChange={setColor} size={30} />
          </div>

          <div className="flex gap-3 mb-3.5">
            <Qty value={qty} onChange={setQty} />
            <Button
              variant="primary"
              size="lg"
              className="flex-grow"
              onClick={() => addToCart(product, color, qty)}
            >
              Add to cart — {money(product.price * qty)}
            </Button>
          </div>

          <div className="flex items-center gap-2 mb-[26px]">
            {product.stock <= 5 ? (
              <span
                className="inline-flex items-center text-[11px] font-bold tracking-[0.04em] uppercase px-[9px] py-[3px] rounded-full text-[oklch(0.50_0.10_60)]"
                style={{ background: "oklch(0.94 0.05 75)" }}
              >
                Only {product.stock} left
              </span>
            ) : (
              <span
                className="inline-flex items-center text-[11px] font-bold tracking-[0.04em] uppercase px-[9px] py-[3px] rounded-full text-ok"
                style={{ background: "oklch(0.93 0.04 150)" }}
              >
                In stock
              </span>
            )}
            <span className="text-muted text-[13px] inline-flex items-center gap-1">
              <Icon name="truck" size={14} /> Free delivery over $75
            </span>
          </div>

          <hr className="border-0 border-t border-line" />
          <div className="flex gap-[22px] mt-4">
            {(["desc", "ship", "care"] as Tab[]).map((k) => (
              <button
                key={k}
                onClick={() => setTab(k)}
                className={
                  "bg-transparent border-0 cursor-pointer text-sm font-semibold py-1 " +
                  (tab === k
                    ? "text-ink border-b-2 border-ink"
                    : "text-muted border-b-2 border-transparent")
                }
              >
                {k === "desc" ? "Description" : k === "ship" ? "Shipping" : "Care"}
              </button>
            ))}
          </div>
          <p className="text-muted text-[15px] leading-relaxed mt-3.5">
            {TAB_COPY[tab](product.blurb)}
          </p>
        </div>
      </div>

      <section className={`${WRAP} mt-[72px]`}>
        <h2 className="display text-3xl mb-[22px]">You may also like</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-7">
          {related.map((r, i) => (
            <ProductCard key={r.id} product={r} delay={i * 60} />
          ))}
        </div>
      </section>
    </div>
  );
};

export default ProductDetails;
