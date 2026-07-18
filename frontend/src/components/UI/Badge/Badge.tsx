import React from 'react';

export type BadgeTone = 'accent' | 'ok' | 'warn' | 'neutral';

type BadgeProps = React.HTMLAttributes<HTMLSpanElement> & {
  tone?: BadgeTone;
};

const TONES: Record<BadgeTone, string> = {
  accent: 'bg-accent-soft text-accent-deep',
  ok: 'text-ok',
  warn: 'text-[oklch(0.50_0.10_60)]',
  neutral: 'bg-paper-2 text-muted',
};

const TONE_STYLE: Partial<Record<BadgeTone, React.CSSProperties>> = {
  ok: { background: 'oklch(0.93 0.04 150)' },
  warn: { background: 'oklch(0.94 0.05 75)' },
};

/** Small uppercase status pill (the design's `.badge`). */
export default function Badge({ tone = 'accent', className = '', style, children, ...rest }: BadgeProps) {
  return (
    <span
      {...rest}
      style={{ ...TONE_STYLE[tone], ...style }}
      className={[
        'inline-flex items-center text-[11px] font-bold tracking-[0.04em] uppercase',
        'px-[9px] py-[3px] rounded-full',
        TONES[tone],
        className,
      ].join(' ')}
    >
      {children}
    </span>
  );
}
