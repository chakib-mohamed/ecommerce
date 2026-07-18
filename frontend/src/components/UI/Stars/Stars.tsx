import Icon from '../Icon/Icon';

interface StarsProps {
  value?: number;
  reviews?: number;
}

/** Five-star rating with an optional "4.5 · 42 reviews" caption. */
export default function Stars({ value = 4.5, reviews }: StarsProps) {
  return (
    <span className="flex items-center gap-1.5 text-[13px] text-muted">
      <span className="inline-flex gap-px text-accent">
        {[0, 1, 2, 3, 4].map((i) => (
          <span key={i} style={{ opacity: i < Math.round(value) ? 1 : 0.25 }}>
            <Icon name="star" size={13} />
          </span>
        ))}
      </span>
      {reviews != null && <span>{value.toFixed(1)} · {reviews} reviews</span>}
    </span>
  );
}
