import { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { useNavigate, useParams } from "react-router-dom";
import ProductCard from "../../components/storefront/ProductCard/ProductCard";
import Button from "../../components/UI/Button/Button";
import ConfirmDialog from "../../components/UI/ConfirmDialog/ConfirmDialog";
import { Textarea } from "../../components/UI/Field/Field";
import Icon from "../../components/UI/Icon/Icon";
import PhotoTile from "../../components/UI/PhotoTile/PhotoTile";
import Qty from "../../components/UI/Qty/Qty";
import StarPicker from "../../components/UI/StarPicker/StarPicker";
import Stars from "../../components/UI/Stars/Stars";
import type { Product } from "../../data/catalog";
import { fetchProductById, fetchProductPage } from "../../lib/catalog-api";
import { money } from "../../lib/money";
import { deleteReview, fetchReviews, submitReview, Review } from "../../lib/reviews-api";
import { useAddToCart } from "../../lib/use-add-to-cart";
import { useCatName, useSubName } from "../../lib/use-catalog";
import { User } from "../../services";
import type { RootState } from "../../store";

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

const fmtReviewDate = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, { month: "short", day: "2-digit", year: "numeric" });

interface ReviewCardProps {
  review: Review;
  onDelete?: () => void;
}
function ReviewCard({ review, onDelete }: ReviewCardProps) {
  return (
    <div className="rounded-md bg-surface border border-line p-4">
      <div className="flex items-start justify-between gap-3 mb-1.5">
        <div>
          <div className="text-sm font-semibold">{review.reviewer}</div>
          <div className="text-muted text-xs mt-0.5">{fmtReviewDate(review.created_at)}</div>
        </div>
        {onDelete && (
          <Button variant="quiet" size="sm" onClick={onDelete}>
            <Icon name="close" size={14} /> Delete
          </Button>
        )}
      </div>
      <Stars value={review.stars} />
      {review.text && <p className="text-ink-2 text-sm mt-2 leading-relaxed">{review.text}</p>}
    </div>
  );
}

const ProductDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const addToCart = useAddToCart();
  const catName = useCatName();
  const subName = useSubName();

  const user = useSelector((state: RootState) => state.login.user);
  const userEmail = user && user !== "anonymous" ? (user as User).email : undefined;

  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [related, setRelated] = useState<Product[]>([]);
  const [qty, setQty] = useState(1);
  const [shot, setShot] = useState(0);
  const [tab, setTab] = useState<Tab>("desc");

  const [reviews, setReviews] = useState<Review[]>([]);
  const [formStars, setFormStars] = useState(0);
  const [formText, setFormText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [deleteReviewId, setDeleteReviewId] = useState<string | null>(null);

  const myReview = userEmail ? reviews.find((r) => r.reviewer === userEmail) : undefined;
  const otherReviews = reviews.filter((r) => r.id !== myReview?.id);

  // Fetch the viewed product by id, resetting selections on change.
  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    setQty(1);
    setShot(0);
    fetchProductById(id)
      .then((p) => {
        if (!cancelled) setProduct(p);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  // "You may also like" — a page of the same category, minus this product.
  useEffect(() => {
    if (!product) {
      setRelated([]);
      return;
    }
    let cancelled = false;
    fetchProductPage({ categoryId: product.cat, page: 0, size: 8 }).then((list) => {
      if (!cancelled) setRelated(list.filter((x) => x.id !== product.id).slice(0, 4));
    });
    return () => {
      cancelled = true;
    };
  }, [product]);

  // Reviews for the viewed product — pre-fills the write-review form from the caller's own
  // review, if they already left one (upsert semantics: resubmitting edits it).
  useEffect(() => {
    if (!id) {
      setReviews([]);
      return;
    }
    let cancelled = false;
    fetchReviews(id).then((list) => {
      if (cancelled) return;
      setReviews(list);
      const mine = userEmail ? list.find((r) => r.reviewer === userEmail) : undefined;
      setFormStars(mine?.stars ?? 0);
      setFormText(mine?.text ?? "");
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const refreshAfterReviewChange = () => {
    if (id) fetchProductById(id).then((p) => p && setProduct(p));
  };

  const handleSubmitReview = () => {
    if (!id || formStars === 0) return;
    setSubmitting(true);
    submitReview({ product_id: id, stars: formStars, text: formText.trim() || undefined })
      .then((saved) => {
        setReviews((prev) => [saved, ...prev.filter((r) => r.id !== saved.id)]);
        refreshAfterReviewChange();
      })
      .catch(() => {
        // The API client surfaces failures via a toast (including the 403 for
        // reviewers who haven't purchased this product).
      })
      .finally(() => setSubmitting(false));
  };

  const confirmDeleteReview = () => {
    if (!deleteReviewId) return;
    const reviewIdToDelete = deleteReviewId;
    deleteReview(reviewIdToDelete)
      .then(() => {
        setReviews((prev) => prev.filter((r) => r.id !== reviewIdToDelete));
        setFormStars(0);
        setFormText("");
        refreshAfterReviewChange();
      })
      .finally(() => setDeleteReviewId(null));
  };

  if (loading) {
    return <div className={`${WRAP} py-24 text-center text-muted`}>Loading product…</div>;
  }

  if (!product) {
    return <div className={`${WRAP} py-24 text-center text-muted`}>Product not found.</div>;
  }

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

          <div className="flex gap-3 mb-3.5">
            <Qty value={qty} onChange={setQty} />
            <Button
              variant="primary"
              size="lg"
              className="flex-grow"
              onClick={() => addToCart(product, qty)}
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
        <h2 className="display text-3xl mb-[22px]">Reviews</h2>

        {userEmail ? (
          <div className="rounded-md bg-surface border border-line p-5 mb-6">
            <h3 className="font-serif text-lg mb-3">{myReview ? "Your review" : "Write a review"}</h3>
            <div className="mb-3">
              <StarPicker value={formStars} onChange={setFormStars} />
            </div>
            <Textarea
              value={formText}
              onChange={(e) => setFormText(e.target.value)}
              placeholder="What did you think of this product? (optional)"
              rows={3}
              maxLength={2000}
              className="mb-3"
            />
            <div className="flex items-center gap-2.5">
              <Button
                variant="primary"
                disabled={formStars === 0 || submitting}
                onClick={handleSubmitReview}
              >
                {submitting ? "Saving…" : myReview ? "Update review" : "Submit review"}
              </Button>
              {myReview && (
                <Button variant="ghost" onClick={() => setDeleteReviewId(myReview.id)}>
                  Delete review
                </Button>
              )}
            </div>
          </div>
        ) : (
          <p className="text-muted text-sm mb-6">
            <Button variant="quiet" size="sm" onClick={() => navigate("/login", { state: { from: `/product/${id}` } })}>
              Log in
            </Button>{" "}
            to write a review.
          </p>
        )}

        {otherReviews.length === 0 && !myReview ? (
          <p className="text-muted text-sm">No reviews yet — be the first.</p>
        ) : (
          <div className="flex flex-col gap-3">
            {otherReviews.map((r) => (
              <ReviewCard key={r.id} review={r} />
            ))}
          </div>
        )}
      </section>

      <section className={`${WRAP} mt-[72px]`}>
        <h2 className="display text-3xl mb-[22px]">You may also like</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-7">
          {related.map((r, i) => (
            <ProductCard key={r.id} product={r} delay={i * 60} />
          ))}
        </div>
      </section>

      {deleteReviewId && (
        <ConfirmDialog
          title="Delete review?"
          message="This removes your review and updates the product's rating."
          confirmLabel="Delete"
          onConfirm={confirmDeleteReview}
          onCancel={() => setDeleteReviewId(null)}
        />
      )}
    </div>
  );
};

export default ProductDetails;
