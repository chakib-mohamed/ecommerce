import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../../../components/UI/Button/Button';
import { buildAnalytics } from '../../../lib/analytics';
import { money } from '../../../lib/money';
import { useCatalogCategories, useCatalogProducts } from '../../../lib/use-catalog';
import CategoryDonut from './CategoryDonut';
import SalesChart from './SalesChart';
import StatCard from './StatCard';

const CARD = 'bg-surface border border-line rounded-md px-6 py-[22px]';
const TH =
  'text-left text-[11px] tracking-[0.06em] uppercase text-muted py-2 font-semibold border-b border-line';

/** Back-office overview — stat cards, sales trend, top sellers + revenue mix. */
export default function Dashboard() {
  const navigate = useNavigate();
  const products = useCatalogProducts();
  const categories = useCatalogCategories();
  const analytics = useMemo(() => buildAnalytics(products, categories), [products, categories]);

  const lowStock = products.filter((p) => p.stock <= 5);
  const subCount = categories.reduce((n, c) => n + c.subs.length, 0);
  const topProducts = analytics.productSales.slice(0, 5);
  const lastMonth = analytics.sales[analytics.sales.length - 1].value;
  const prevMonth = analytics.sales[analytics.sales.length - 2].value;
  const monthDelta = ((lastMonth - prevMonth) / prevMonth) * 100;
  const totalUnits = analytics.productSales.reduce((s, p) => s + p.units, 0);
  const avgOrder = totalUnits ? Math.round(analytics.totalRevenue / totalUnits) : 0;

  return (
    <div className="px-10 py-9 max-w-[1100px] flex-1">
      <div className="mb-7 reveal">
        <span className="eyebrow">Overview</span>
        <h1 className="display text-4xl mt-1.5">Dashboard</h1>
      </div>

      {/* stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3.5 mb-6 reveal" style={{ animationDelay: '40ms' }}>
        <StatCard
          label="Revenue (12 mo)"
          value={money(analytics.totalRevenue)}
          sub={`${monthDelta >= 0 ? '+' : ''}${monthDelta.toFixed(1)}% vs last month`}
        />
        <StatCard
          label="Products"
          value={products.length}
          sub={`${lowStock.length} low stock`}
          onClick={() => navigate('/admin/products')}
        />
        <StatCard
          label="Categories"
          value={categories.length}
          sub={`${subCount} subcategories`}
          onClick={() => navigate('/admin/categories')}
        />
        <StatCard label="Avg order" value={money(avgOrder)} sub="per unit" />
      </div>

      {/* sales evolution */}
      <div className={`${CARD} mb-6 reveal`} style={{ animationDelay: '80ms' }}>
        <div className="flex items-center justify-between mb-4">
          <div>
            <span className="eyebrow">Last 12 months</span>
            <div className="text-base font-semibold mt-1">Sales evolution</div>
          </div>
          <div className="flex gap-2 items-baseline">
            <span className="display text-[22px]">{money(lastMonth)}</span>
            <span className={`text-xs font-semibold ${monthDelta >= 0 ? 'text-ok' : 'text-accent'}`}>
              {monthDelta >= 0 ? '↗' : '↘'} {Math.abs(monthDelta).toFixed(1)}%
            </span>
          </div>
        </div>
        <SalesChart data={analytics.sales} />
      </div>

      {/* top products + category donut */}
      <div className="grid grid-cols-1 lg:grid-cols-[1.4fr_1fr] gap-5 mb-6 reveal" style={{ animationDelay: '120ms' }}>
        <div className={CARD}>
          <div className="flex items-center justify-between mb-4">
            <div>
              <span className="eyebrow">Top sellers</span>
              <div className="text-base font-semibold mt-1">Best selling products</div>
            </div>
            <Button variant="quiet" size="sm" onClick={() => navigate('/admin/products')}>
              View all
            </Button>
          </div>
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr>
                <th className={TH}>#</th>
                <th className={TH}>Product</th>
                <th className={`${TH} !text-right`}>Units</th>
                <th className={`${TH} !text-right`}>Revenue</th>
              </tr>
            </thead>
            <tbody>
              {topProducts.map((p, i) => (
                <tr key={p.id} className={i < topProducts.length - 1 ? 'border-b border-line' : ''}>
                  <td className="py-3 text-muted w-7">{i + 1}</td>
                  <td className="py-3 font-medium">{p.name}</td>
                  <td className="py-3 text-right">{p.units}</td>
                  <td className="py-3 text-right font-medium">{money(p.revenue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className={CARD}>
          <div className="mb-4">
            <span className="eyebrow">By category</span>
            <div className="text-base font-semibold mt-1">Revenue breakdown</div>
          </div>
          <CategoryDonut data={analytics.catBreakdown} />
        </div>
      </div>

      {/* low stock */}
      {lowStock.length > 0 && (
        <div className={`${CARD} mb-6 reveal`} style={{ animationDelay: '160ms' }}>
          <div className="flex items-center justify-between mb-3.5">
            <span className="text-[15px] font-semibold">Low stock alerts</span>
            <span className="text-muted text-[13px]">{lowStock.length} items</span>
          </div>
          <div className="grid gap-2">
            {lowStock.map((p) => (
              <div key={p.id} className="flex items-center justify-between text-sm">
                <span>{p.name}</span>
                <span className="text-accent font-semibold">{p.stock} left</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* quick actions */}
      <div className={`${CARD} reveal`} style={{ animationDelay: '200ms' }}>
        <div className="text-[15px] font-semibold mb-3.5">Quick actions</div>
        <div className="flex gap-2.5">
          <Button variant="ghost" onClick={() => navigate('/admin/products')}>
            View products
          </Button>
          <Button variant="ghost" onClick={() => navigate('/admin/categories')}>
            Manage categories
          </Button>
        </div>
      </div>
    </div>
  );
}
