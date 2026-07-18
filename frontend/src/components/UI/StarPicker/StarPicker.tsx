import { useState } from 'react';
import Icon from '../Icon/Icon';

interface StarPickerProps {
  value: number;
  onChange: (value: number) => void;
}

/** Clickable 1-5 star rating input for the review form — hover previews the pick. */
export default function StarPicker({ value, onChange }: StarPickerProps) {
  const [hover, setHover] = useState<number | null>(null);
  const shown = hover ?? value;

  return (
    <span className="inline-flex gap-1 text-accent" onMouseLeave={() => setHover(null)}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          onClick={() => onChange(n)}
          onMouseEnter={() => setHover(n)}
          className="bg-transparent border-0 p-0 cursor-pointer"
          aria-label={`${n} star${n === 1 ? '' : 's'}`}
        >
          <span style={{ opacity: n <= shown ? 1 : 0.25 }}>
            <Icon name="star" size={22} />
          </span>
        </button>
      ))}
    </span>
  );
}
