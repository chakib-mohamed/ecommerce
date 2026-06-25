import { useNavigate } from "react-router-dom";
import { shop } from "../../../data/catalog";
import { useCatalogCategories } from "../../../lib/use-catalog";
import Logo from "../../UI/Logo/Logo";

/** Storefront footer — wordmark + tagline and the Shop (categories) column. */
export default function Footer() {
  const navigate = useNavigate();
  const categories = useCatalogCategories();
  return (
    <footer className="mt-[90px] border-t border-line bg-paper">
      <div className="max-w-[1180px] mx-auto px-6 py-12 grid grid-cols-1 sm:grid-cols-[2fr_1fr_1fr_1fr] gap-8">
        <div>
          <Logo onClick={() => navigate("/")} />
          <p className="text-muted text-sm max-w-[240px] mt-3">
            {shop.tagline} A placeholder demo shop.
          </p>
        </div>
        <div>
          <div className="eyebrow mb-3">Shop</div>
          <div className="flex flex-col gap-2">
            {categories.map((c) => (
              <button
                key={c.id}
                onClick={() => navigate(`/browse/${c.id}`)}
                className="text-left bg-transparent border-0 cursor-pointer text-muted text-sm hover:text-ink w-fit"
              >
                {c.name}
              </button>
            ))}
          </div>
        </div>
      </div>
      <div className="border-t border-line py-[18px] px-6 text-center text-muted">
        <span className="text-[13px]">© 2026 Cloud Shop · Wireframe-to-prototype demo</span>
      </div>
    </footer>
  );
}
