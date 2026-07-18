import Icon from '../Icon/Icon';
import IconButton from '../IconButton/IconButton';

interface QtyProps {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
}

/** Pill quantity stepper with +/- controls and min/max bounds. */
export default function Qty({ value, onChange, min = 1, max = 99 }: QtyProps) {
  return (
    <div className="inline-flex items-center rounded-full border-[1.5px] border-line-2 p-[3px]">
      <IconButton
        size={32}
        disabled={value <= min}
        onClick={() => onChange(Math.max(min, value - 1))}
        aria-label="decrease"
      >
        <Icon name="minus" size={16} />
      </IconButton>
      <span className="price min-w-[26px] text-center font-semibold">{value}</span>
      <IconButton
        size={32}
        disabled={value >= max}
        onClick={() => onChange(Math.min(max, value + 1))}
        aria-label="increase"
      >
        <Icon name="plus" size={16} />
      </IconButton>
    </div>
  );
}
