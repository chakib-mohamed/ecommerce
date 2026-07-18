import React from 'react';

type ChipProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  /** toggled state — drives the filled "is-on" look */
  active?: boolean;
};

/** Pill toggle (the design's `.chip`), filled ink when `active`. */
export default function Chip({ active = false, className = '', children, ...rest }: ChipProps) {
  return (
    <button
      type="button"
      aria-pressed={active}
      {...rest}
      className={[
        'inline-flex items-center gap-1.5 text-[13px] font-medium px-[13px] py-1.5 rounded-full',
        'border-[1.5px] cursor-pointer whitespace-nowrap transition-all duration-150 ease-editorial',
        active
          ? 'bg-ink text-paper border-ink'
          : 'bg-surface text-ink-2 border-line-2 hover:border-ink',
        className,
      ].join(' ')}
    >
      {children}
    </button>
  );
}
