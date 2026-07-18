import React from 'react';

export type ButtonVariant = 'outline' | 'primary' | 'accent' | 'ghost' | 'quiet';
export type ButtonSize = 'md' | 'sm' | 'lg';

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  block?: boolean;
};

const BASE =
  'inline-flex items-center justify-center gap-2 font-semibold tracking-[0.01em] rounded-full ' +
  'border-[1.5px] cursor-pointer whitespace-nowrap select-none ' +
  'transition-[transform,background,color,border-color,box-shadow] duration-200 ease-editorial ' +
  'hover:-translate-y-px active:translate-y-0 disabled:opacity-40 disabled:cursor-not-allowed ' +
  'disabled:transform-none';

const VARIANTS: Record<ButtonVariant, string> = {
  outline: 'border-ink bg-transparent text-ink hover:bg-paper-2',
  primary: 'bg-ink text-paper border-ink hover:bg-accent-deep hover:border-accent-deep',
  accent: 'bg-accent text-white border-accent hover:bg-accent-deep hover:border-accent-deep',
  ghost: 'border-line-2 text-ink bg-transparent hover:border-ink',
  quiet: 'border-transparent text-ink px-1.5 hover:text-accent-deep',
};

const SIZES: Record<ButtonSize, string> = {
  md: 'text-sm px-5 py-[11px]',
  sm: 'text-[13px] px-3.5 py-[7px]',
  lg: 'text-[15px] px-[26px] py-3.5',
};

/** The design's `.btn` family — pill buttons with variant + size modifiers. */
export default function Button({
  variant = 'outline',
  size = 'md',
  block = false,
  className = '',
  type = 'button',
  children,
  ...rest
}: ButtonProps) {
  const sizeClass = variant === 'quiet' ? SIZES[size].replace(/px-\S+/, '') : SIZES[size];
  return (
    <button
      type={type}
      {...rest}
      className={[BASE, VARIANTS[variant], sizeClass, block ? 'w-full' : '', className].join(' ')}
    >
      {children}
    </button>
  );
}
