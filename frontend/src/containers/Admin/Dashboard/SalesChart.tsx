import type { MonthSale } from '../../../lib/analytics';

const W = 600;
const H = 200;
const PAD_L = 40;
const PAD_R = 16;
const PAD_T = 16;
const PAD_B = 28;
const INNER_W = W - PAD_L - PAD_R;
const INNER_H = H - PAD_T - PAD_B;

/** SVG line chart — 12-month sales with a gradient area fill and dot markers. */
export default function SalesChart({ data }: { data: MonthSale[] }) {
  const max = Math.max(...data.map((d) => d.value)) * 1.1;
  const min = Math.min(...data.map((d) => d.value)) * 0.85;
  const x = (i: number) => PAD_L + (i / (data.length - 1)) * INNER_W;
  const y = (v: number) => PAD_T + INNER_H - ((v - min) / (max - min)) * INNER_H;

  const path = data.map((d, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(d.value)}`).join(' ');
  const area = `${path} L ${x(data.length - 1)} ${PAD_T + INNER_H} L ${x(0)} ${PAD_T + INNER_H} Z`;
  const gridLines = [0, 0.25, 0.5, 0.75, 1].map((t) => PAD_T + t * INNER_H);

  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-auto block">
      <defs>
        <linearGradient id="salesGrad" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.18" />
          <stop offset="100%" stopColor="var(--accent)" stopOpacity="0" />
        </linearGradient>
      </defs>
      {gridLines.map((gy, i) => (
        <line key={i} x1={PAD_L} x2={W - PAD_R} y1={gy} y2={gy} stroke="var(--line)" strokeWidth="1" />
      ))}
      <path d={area} fill="url(#salesGrad)" />
      <path
        d={path}
        fill="none"
        stroke="var(--accent)"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {data.map((d, i) => (
        <circle key={i} cx={x(i)} cy={y(d.value)} r="3" fill="var(--paper)" stroke="var(--accent)" strokeWidth="2" />
      ))}
      {data.map((d, i) => (
        <text key={i} x={x(i)} y={H - 8} textAnchor="middle" fontSize="10" fill="var(--muted)" fontFamily="inherit">
          {d.month}
        </text>
      ))}
    </svg>
  );
}
