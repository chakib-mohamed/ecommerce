import React from 'react';

interface PhotoTileProps {
  /** real product image URL — when set, renders the image instead of the placeholder */
  src?: string;
  /** color tone 1..6 */
  tone?: number;
  /** product name — first letter becomes the placeholder glyph (and image alt) */
  name?: string;
  label?: string;
  glyph?: string;
  className?: string;
  style?: React.CSSProperties;
}

/**
 * Product "photo" tile. Renders the real image when `src` is provided, otherwise
 * the design's colored `.ph` placeholder (tone keys the background palette).
 */
export default function PhotoTile({
  src,
  tone = 1,
  name = '',
  label = 'product photo',
  glyph,
  className,
  style,
}: PhotoTileProps) {
  if (src) {
    return (
      <img
        src={src}
        alt={name || label}
        loading="lazy"
        className={'w-full h-full object-cover' + (className ? ' ' + className : '')}
        style={style}
      />
    );
  }
  const ch = glyph != null ? glyph : name ? name.trim()[0] : '◍';
  return (
    <div className={'ph' + (className ? ' ' + className : '')} data-tone={tone} style={style}>
      <span className="ph__mono" aria-hidden="true">{ch}</span>
      <span className="ph__tag">{label}</span>
    </div>
  );
}
