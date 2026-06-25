import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../../../components/UI/Button/Button';
import Icon from '../../../components/UI/Icon/Icon';
import { categories, products } from '../../../data/catalog';
import { money } from '../../../lib/money';

const TH = 'py-2.5 px-5 font-semibold text-xs tracking-[0.05em] uppercase text-muted whitespace-nowrap';

/** Admin product list — search + category/subcategory filters over a tree of
 *  collapsible category panels, each grouping its subcategories' products. */
export default function AdminProducts() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState('');
  const [catFilter, setCatFilter] = useState('');
  const [subFilter, setSubFilter] = useState('');
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  const toggleCat = (key: string) => setCollapsed((c) => ({ ...c, [key]: !c[key] }));
  const activeCat = categories.find((c) => c.id === catFilter);

  const filtered = products.filter(
    (p) =>
      p.name.toLowerCase().includes(filter.toLowerCase()) &&
      (!catFilter || p.cat === catFilter) &&
      (!subFilter || p.sub === subFilter),
  );

  const grouped = categories
    .map((cat) => ({
      ...cat,
      subs: cat.subs
        .map((sub) => ({
          ...sub,
          products: filtered.filter((p) => p.cat === cat.id && p.sub === sub.id),
        }))
        .filter((sub) => sub.products.length > 0),
    }))
    .filter((cat) => cat.subs.length > 0);

  const clearFilters = () => {
    setCatFilter('');
    setSubFilter('');
    setFilter('');
  };

  return (
    <div className="px-10 py-9 max-w-[960px] flex-1">
      <div className="flex items-end justify-between mb-7 reveal">
        <div>
          <span className="eyebrow">Manage</span>
          <h1 className="display text-4xl mt-1.5">Products</h1>
        </div>
        <Button variant="primary" onClick={() => navigate('/admin/products/new')}>
          <Icon name="plus" size={16} /> New product
        </Button>
      </div>

      {/* search + filters */}
      <div className="bg-surface border border-line rounded-md overflow-hidden mb-4 reveal" style={{ animationDelay: '40ms' }}>
        <div className="flex items-center gap-2.5 px-5 py-3 border-b border-line">
          <Icon name="search" size={15} />
          <input
            type="text"
            placeholder="Search products…"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="border-0 outline-none text-sm flex-1 bg-transparent placeholder:text-faint"
          />
          <span className="text-muted text-[13px]">{filtered.length} items</span>
        </div>
        <div className="flex flex-wrap items-center gap-2.5 px-5 py-2.5">
          <select
            value={catFilter}
            onChange={(e) => {
              setCatFilter(e.target.value);
              setSubFilter('');
            }}
            className={`px-3 py-[7px] rounded-sm border-[1.5px] border-line-2 text-[13px] cursor-pointer ${
              catFilter ? 'bg-ink text-paper' : 'bg-paper text-ink'
            }`}
          >
            <option value="">All categories</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          <select
            value={subFilter}
            onChange={(e) => setSubFilter(e.target.value)}
            disabled={!activeCat}
            className={`px-3 py-[7px] rounded-sm border-[1.5px] border-line-2 text-[13px] disabled:opacity-40 ${
              subFilter ? 'bg-ink text-paper' : 'bg-paper text-ink'
            } ${activeCat ? 'cursor-pointer' : 'cursor-default'}`}
          >
            <option value="">All subcategories</option>
            {activeCat?.subs.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          {(catFilter || subFilter || filter) && (
            <Button variant="quiet" size="sm" onClick={clearFilters}>
              Clear filters
            </Button>
          )}
        </div>
      </div>

      {/* grouped tables */}
      <div className="grid gap-3">
        {grouped.map((cat) => {
          const count = cat.subs.reduce((n, s) => n + s.products.length, 0);
          const isCollapsed = collapsed[cat.id];
          return (
            <div key={cat.id} className="bg-surface border-2 border-line rounded-md overflow-hidden">
              <button
                onClick={() => toggleCat(cat.id)}
                className="flex items-center justify-between w-full px-5 py-3.5 border-0 bg-paper-2 cursor-pointer"
              >
                <span className="font-serif text-base font-semibold">{cat.name}</span>
                <div className="flex items-center gap-2.5">
                  <span className="text-muted text-[13px]">{count} products</span>
                  <span
                    className="text-[11px] text-muted inline-block transition-transform duration-150"
                    style={{ transform: isCollapsed ? 'rotate(-90deg)' : 'none' }}
                  >
                    ▼
                  </span>
                </div>
              </button>

              {!isCollapsed &&
                cat.subs.map((sub) => (
                  <div key={sub.id}>
                    <div className="flex items-center gap-2.5 px-5 pl-7 py-3 text-[13px] font-bold tracking-[0.08em] uppercase text-ink border-t border-line bg-paper-2">
                      <span className="w-[3px] h-3.5 bg-accent rounded-sm inline-block" />
                      {sub.name}
                      <span className="ml-auto text-[11px] font-medium text-muted tracking-[0.04em]">
                        {sub.products.length} item{sub.products.length === 1 ? '' : 's'}
                      </span>
                    </div>
                    <table className="w-full border-collapse text-sm">
                      <thead>
                        <tr className="border-b border-line">
                          <th className={`${TH} text-left pl-7`} />
                          <th className={`${TH} text-right`}>Price</th>
                          <th className={`${TH} text-right`}>Stock</th>
                          <th className={`${TH} w-[60px]`} />
                        </tr>
                      </thead>
                      <tbody>
                        {sub.products.map((p, i) => (
                          <tr
                            key={p.id}
                            className="border-t border-line"
                            style={{ background: i % 2 === 0 ? 'transparent' : 'rgba(0,0,0,0.015)' }}
                          >
                            <td className="py-3 px-5 pl-7 font-medium">{p.name}</td>
                            <td className="py-3 px-5 text-right">{money(p.price)}</td>
                            <td className="py-3 px-5 text-right">
                              <span className={p.stock <= 5 ? 'text-accent font-semibold' : 'text-ink'}>{p.stock}</span>
                            </td>
                            <td className="py-3 px-5 text-right">
                              <Button variant="quiet" size="sm" onClick={() => navigate(`/admin/products/${p.id}/edit`)}>
                                Edit
                              </Button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ))}
            </div>
          );
        })}
        {grouped.length === 0 && <div className="text-center py-10 text-muted">No products found</div>}
      </div>
    </div>
  );
}
