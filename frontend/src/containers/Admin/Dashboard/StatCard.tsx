interface StatCardProps {
  label: string;
  value: string | number;
  sub?: string;
  /** Render the inverted ink-on-paper highlight style. */
  accent?: boolean;
  onClick?: () => void;
}

/** Dashboard metric tile — surface card, or an inverted ink card when `accent`. */
export default function StatCard({ label, value, sub, accent, onClick }: StatCardProps) {
  return (
    <div
      onClick={onClick}
      className={[
        'rounded-md px-5 py-[22px] transition-[transform,box-shadow] duration-150',
        accent ? 'bg-ink text-paper border-0' : 'bg-surface text-ink border border-line',
        onClick ? 'cursor-pointer hover:-translate-y-0.5 hover:shadow-pop' : '',
      ].join(' ')}
    >
      <div className="text-xs tracking-[0.07em] uppercase opacity-60 mb-3">{label}</div>
      <div className="display text-4xl leading-none">{value}</div>
      {sub && <div className="text-xs mt-2 opacity-55">{sub}</div>}
    </div>
  );
}
