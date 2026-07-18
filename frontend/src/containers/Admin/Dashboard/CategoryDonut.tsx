import type { CategoryRevenue } from '../../../lib/analytics';
import { money } from '../../../lib/money';

const SIZE = 180;
const R = 70;
const CX = SIZE / 2;
const CY = SIZE / 2;
const SW = 22;
const CIRC = 2 * Math.PI * R;

/** Fixed warm series palette for the donut slices + legend swatches. */
const COLORS = ['#a65a3a', '#7d7f5a', '#6f7b85', '#b07a5b', '#8a9479'];

/** SVG donut (revenue share by category) with a center total and a % legend. */
export default function CategoryDonut({ data }: { data: CategoryRevenue[] }) {
  const total = data.reduce((s, d) => s + d.value, 0);
  let cum = 0;

  return (
    <div className="flex items-center gap-5">
      <svg viewBox={`0 0 ${SIZE} ${SIZE}`} className="w-[150px] h-[150px] flex-shrink-0">
        <circle cx={CX} cy={CY} r={R} fill="none" stroke="var(--paper-2)" strokeWidth={SW} />
        {data.map((d, i) => {
          const frac = total ? d.value / total : 0;
          const offset = cum * CIRC;
          const len = frac * CIRC;
          cum += frac;
          return (
            <circle
              key={d.id}
              cx={CX}
              cy={CY}
              r={R}
              fill="none"
              stroke={COLORS[i % COLORS.length]}
              strokeWidth={SW}
              strokeDasharray={`${len} ${CIRC - len}`}
              strokeDashoffset={-offset}
              transform={`rotate(-90 ${CX} ${CY})`}
            />
          );
        })}
        <text x={CX} y={CY - 4} textAnchor="middle" fontSize="11" fill="var(--muted)" letterSpacing="0.06em">
          TOTAL
        </text>
        <text x={CX} y={CY + 14} textAnchor="middle" fontSize="16" fontWeight="600" fill="var(--ink)">
          {money(total)}
        </text>
      </svg>
      <div className="grid gap-2.5 flex-1 min-w-0">
        {data.map((d, i) => (
          <div key={d.id} className="flex justify-between items-center text-[13px] gap-2">
            <div className="flex gap-2 items-center min-w-0 overflow-hidden">
              <span
                className="w-2.5 h-2.5 rounded-[2px] flex-shrink-0"
                style={{ background: COLORS[i % COLORS.length] }}
              />
              <span className="overflow-hidden text-ellipsis whitespace-nowrap">{d.name}</span>
            </div>
            <span className="text-muted flex-shrink-0">{d.pct.toFixed(1)}%</span>
          </div>
        ))}
      </div>
    </div>
  );
}
