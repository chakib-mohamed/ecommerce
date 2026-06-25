import React from 'react';

interface PhotoTileProps {
  /** color tone 1..6 */
  tone?: number;
  /** product name — first letter becomes the placeholder glyph */
  name?: string;
  label?: string;
  glyph?: string;
  className?: string;
  style?: React.CSSProperties;
}

/**
 * Colored placeholder "photo" tile (the design's `.ph`). Swap for real product
 * imagery once available; tone keys the background palette.
 */
export default function PhotoTile({
  tone = 1,
  name = '',
  label = 'product photo',
  glyph,
  className,
  style,
}: PhotoTileProps) {
  const ch = glyph != null ? glyph : name ? name.trim()[0] : '◍';
  return (
    <div className={'ph' + (className ? ' ' + className : '')} data-tone={tone} style={style}>
      <span className="ph__mono" aria-hidden="true">{ch}</span>
      <span className="ph__tag">{label}</span>
    </div>
  );
}
