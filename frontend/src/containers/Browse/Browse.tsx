import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import ProductCard from "../../components/storefront/ProductCard/ProductCard";
import { Select } from "../../components/UI/Field/Field";
import { useCatalogCategories, useCatalogProducts, useCatName, useSubName } from "../../lib/use-catalog";

const WRAP = "max-w-[1180px] mx-auto px-6";

type Sort = "featured" | "low" | "high";

interface TreeLinkProps {
  label: string;
  active?: boolean;
  bold?: boolean;
  small?: boolean;
  onClick: () => void;
}

function TreeLink({ label, active, bold, small, onClick }: TreeLinkProps) {
  return (
    <button
      onClick={onClick}
      className={[
        "text-left border-0 cursor-pointer w-full px-2.5 py-[7px] rounded-sm transition-colors duration-150",
        small ? "text-sm" : "text-[15px]",
        bold || active ? "font-semibold" : "font-normal",
        active ? "bg-paper-2 text-ink" : "bg-transparent text-ink-2 hover:bg-paper-2",
      ].join(" ")}
    >
      {label}
    </button>
  );
}

/** Browse view — sticky category/colour sidebar + a filtered, sortable grid. */
const Browse: React.FC = () => {
  const navigate = useNavigate();
  const { cat, sub } = useParams<{ cat?: string; sub?: string }>();
  const products = useCatalogProducts();
  const categories = useCatalogCategories();
  const catName = useCatName();
  const subName = useSubName();
  const [sort, setSort] = useState<Sort>("featured");

  let items = products.filter(
    (p) => (!cat || p.cat === cat) && (!sub || p.sub === sub)
  );
  if (sort === "low") items = [...items].sort((a, b) => a.price - b.price);
  if (sort === "high") items = [...items].sort((a, b) => b.price - a.price);
  if (sort === "featured")
    items = [...items].sort((a, b) => (b.featured ? 1 : 0) - (a.featured ? 1 : 0));

  const title = sub ? subName(cat!, sub) : cat ? catName(cat) : "All products";

  return (
    <div>
      <div className={`${WRAP} pt-7`}>
        <div className="text-muted text-[13px] reveal">
          <button className="bg-transparent border-0 cursor-pointer text-muted p-0" onClick={() => navigate("/")}>
            Home
          </button>
          {cat && (
            <>
              {" / "}
              <button
                className="bg-transparent border-0 cursor-pointer text-muted p-0"
                onClick={() => navigate(`/browse/${cat}`)}
              >
                {catName(cat)}
              </button>
            </>
          )}
          {sub && <> / <span className="text-ink">{subName(cat!, sub)}</span></>}
        </div>
        <h1 className="display text-[44px] mt-2.5 mb-7 reveal">{title}</h1>
      </div>

      <div
        className={`${WRAP} grid grid-cols-1 md:grid-cols-[220px_1fr] gap-10 items-start pb-20`}
      >
        <aside className="reveal md:sticky md:top-[88px]">
          <div className="eyebrow mb-3">Categories</div>
          <div className="flex flex-col gap-0.5">
            <TreeLink label="All products" active={!cat} onClick={() => navigate("/browse")} />
            {categories.map((cc) => (
              <div key={cc.id}>
                <TreeLink
                  label={cc.name}
                  bold
                  active={cat === cc.id && !sub}
                  onClick={() => navigate(`/browse/${cc.id}`)}
                />
                {cat === cc.id && (
                  <div className="flex flex-col gap-px ml-2.5 border-l border-line pl-2 mt-0.5">
                    {cc.subs.map((s) => (
                      <TreeLink
                        key={s.id}
                        label={s.name}
                        small
                        active={sub === s.id}
                        onClick={() => navigate(`/browse/${cc.id}/${s.id}`)}
                      />
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        </aside>

        <div>
          <div className="flex items-center justify-between mb-5">
            <span className="text-muted text-sm">
              {items.length} {items.length === 1 ? "item" : "items"}
            </span>
            <Select
              style={{ width: "auto" }}
              value={sort}
              onChange={(e) => setSort(e.target.value as Sort)}
            >
              <option value="featured">Sort: Featured</option>
              <option value="low">Price: low to high</option>
              <option value="high">Price: high to low</option>
            </Select>
          </div>
          {items.length === 0 ? (
            <div className="rounded-md bg-surface border border-line p-12 text-center">
              <p className="text-muted m-0">
                Nothing here yet.{" "}
                <button
                  className="text-ink hover:text-accent-deep bg-transparent border-0 cursor-pointer p-0 underline"
                  onClick={() => navigate("/browse")}
                >
                  See everything
                </button>
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-2 lg:grid-cols-3 gap-7">
              {items.map((p, i) => (
                <ProductCard key={p.id} product={p} delay={i * 50} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Browse;
