import { SWATCHES, type Swatch } from '../../../data/catalog';

interface ColorDotsProps {
  colors: Swatch[];
  value?: Swatch;
  onChange?: (color: Swatch) => void;
  size?: number;
}

/** Round color-swatch selector (the design's `.swatch-btn` row). */
export default function ColorDots({ colors, value, onChange, size = 26 }: ColorDotsProps) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {colors.map((c) => {
        const hex = SWATCHES[c] ?? '#ccc';
        const pale = hex === '#efe7d6' || hex === '#d8c9af';
        const pressed = value === c;
        return (
          <button
            key={c}
            type="button"
            title={c}
            aria-pressed={pressed}
            onClick={() => onChange?.(c)}
            className="rounded-full p-0 cursor-pointer transition-transform duration-100 hover:scale-110"
            style={{
              width: size,
              height: size,
              background: hex,
              border: `1.5px solid ${pale ? 'var(--line-2)' : 'transparent'}`,
              boxShadow: pressed ? '0 0 0 2px var(--paper), 0 0 0 4px var(--ink)' : undefined,
            }}
          />
        );
      })}
    </div>
  );
}
