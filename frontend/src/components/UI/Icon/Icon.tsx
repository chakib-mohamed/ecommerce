import React from 'react';

/** Line-icon set ported from the Cloud Shop design (store-components.jsx). */
export type IconName =
  | 'cart' | 'heart' | 'search' | 'user' | 'menu' | 'close' | 'plus'
  | 'minus' | 'arrow' | 'back' | 'check' | 'star' | 'chevron' | 'grid'
  | 'truck' | 'lock';

const PATHS: Record<IconName, React.ReactNode> = {
  cart: <><circle cx="9" cy="20" r="1.4" /><circle cx="18" cy="20" r="1.4" /><path d="M2 3h3l2.2 12.2a1.5 1.5 0 0 0 1.5 1.3h8.3a1.5 1.5 0 0 0 1.5-1.2L21 7H6" /></>,
  heart: <path d="M12 20s-7-4.4-9.3-8.6C1 8 2.7 4.5 6.1 4.5c2 0 3.3 1.2 3.9 2.3.6-1.1 1.9-2.3 3.9-2.3 3.4 0 5.1 3.5 3.4 6.9C19 15.6 12 20 12 20z" />,
  search: <><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.6-3.6" /></>,
  user: <><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 3.6-6.5 8-6.5S20 17 20 21" /></>,
  menu: <path d="M3 6h18M3 12h18M3 18h18" />,
  close: <path d="M6 6l12 12M18 6L6 18" />,
  plus: <path d="M12 5v14M5 12h14" />,
  minus: <path d="M5 12h14" />,
  arrow: <path d="M5 12h14M13 6l6 6-6 6" />,
  back: <path d="M19 12H5M11 6l-6 6 6 6" />,
  check: <path d="M4 12l5 5L20 6" />,
  star: <path d="M12 3l2.6 5.6 6 .7-4.5 4.1 1.2 6L12 16.9 6.7 19.4l1.2-6L3.4 9.3l6-.7L12 3z" />,
  chevron: <path d="M9 6l6 6-6 6" />,
  grid: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
  truck: <><rect x="1" y="6" width="13" height="10" rx="1" /><path d="M14 9h4l3 3v4h-7" /><circle cx="6" cy="18" r="1.6" /><circle cx="17" cy="18" r="1.6" /></>,
  lock: <><rect x="4" y="10" width="16" height="10" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3" /></>,
};

interface IconProps {
  name: IconName;
  size?: number;
  stroke?: number;
  className?: string;
}

export default function Icon({ name, size = 20, stroke = 1.6, className }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={name === 'star' ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={stroke}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      {PATHS[name]}
    </svg>
  );
}
