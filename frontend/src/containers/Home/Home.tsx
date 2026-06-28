import ProductCard from "../../components/storefront/ProductCard/ProductCard";
import Icon, { type IconName } from "../../components/UI/Icon/Icon";
import type { Product } from "../../data/catalog";
import { useCatalogProducts } from "../../lib/use-catalog";

const WRAP = "max-w-[1180px] mx-auto px-6";

const EDITORIAL: [IconName, string, string][] = [
  ["truck", "Considered shipping", "Carbon-neutral delivery, plastic-free packing, always."],
  ["heart", "Made to be kept", "Repairable, refillable and built from materials that age well."],
  ["lock", "Small by design", "A personal shop. You can reach a real person any day of the week."],
];

/** Storefront home — a personalised "For you" feed of scroll rows + a grid. */
const Home: React.FC = () => {
  const products = useCatalogProducts();
  const scrollRow = (slice: Product[], base: number) => (
    <div className="flex gap-4 overflow-x-auto pb-2">
      {slice.map((p, i) => (
        <div key={p.id} className="shrink-0 basis-[140px]">
          <ProductCard product={p} delay={(base + i) * 40} />
        </div>
      ))}
    </div>
  );

  return (
    <div>
      <section className={WRAP}>
        <div className="reveal mt-6 mb-8">
          <h1 className="display text-5xl mb-2">For you</h1>
          <p className="text-muted text-[15px] m-0">rows tuned to your browsing</p>
        </div>
      </section>

      <section className={WRAP}>
        <div className="reveal mb-7">
          <h3 className="font-serif text-xl mb-4">Because you liked Kitchen</h3>
          {scrollRow(products.slice(0, 5), 0)}
        </div>
      </section>

      <section className={WRAP}>
        <div className="reveal mb-7">
          <h3 className="font-serif text-xl mb-4">New in Home &amp; Living</h3>
          {scrollRow(products.slice(5, 10), 5)}
        </div>
      </section>

      <section className={WRAP}>
        <div className="reveal mb-7">
          <h3 className="font-serif text-xl mb-4">Picked for you</h3>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-7">
            {products.slice(10, 14).map((p, i) => (
              <ProductCard key={p.id} product={p} delay={(i + 10) * 40} />
            ))}
          </div>
        </div>
      </section>

      <section className="bg-paper-2 mt-20 py-16">
        <div className={`${WRAP} grid grid-cols-1 md:grid-cols-3 gap-10`}>
          {EDITORIAL.map(([icon, title, copy], i) => (
            <div key={title} className="reveal" style={{ animationDelay: i * 80 + "ms" }}>
              <span className="text-accent">
                <Icon name={icon} size={26} />
              </span>
              <h3 className="font-serif text-[23px] mt-3 mb-1.5">{title}</h3>
              <p className="text-muted text-[15px] m-0">{copy}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
};

export default Home;
