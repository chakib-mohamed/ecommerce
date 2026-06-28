import React from 'react';

type IconButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  size?: number;
};

/**
 * Round, borderless icon button (the design's `.iconbtn`). Subtle paper hover;
 * `position: relative` so callers can overlay a count badge.
 */
export default function IconButton({
  size = 40,
  className = '',
  style,
  children,
  ...rest
}: IconButtonProps) {
  return (
    <button
      {...rest}
      className={
        'relative inline-grid place-items-center rounded-full border-[1.5px] border-transparent ' +
        'bg-transparent text-ink cursor-pointer transition-colors duration-200 ' +
        'hover:bg-paper-2 disabled:opacity-40 disabled:cursor-not-allowed ' +
        className
      }
      style={{ width: size, height: size, ...style }}
    >
      {children}
    </button>
  );
}
