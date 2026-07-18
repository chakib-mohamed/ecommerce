import React from 'react';

const CONTROL =
  'w-full font-sans text-[15px] text-ink bg-surface rounded-sm px-[13px] py-[11px] ' +
  'border-[1.5px] border-line-2 transition-[border-color,box-shadow] duration-150 ease-editorial ' +
  'placeholder:text-faint outline-none ' +
  'focus:border-accent focus:shadow-[0_0_0_3px_var(--accent-soft)]';

const SELECT_CHEVRON = 'select-chevron';

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className = '', ...rest }, ref) => (
    <input ref={ref} {...rest} className={[CONTROL, className].join(' ')} />
  ),
);
Input.displayName = 'Input';

export const Select = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className = '', children, ...rest }, ref) => (
    <select ref={ref} {...rest} className={[CONTROL, SELECT_CHEVRON, className].join(' ')}>
      {children}
    </select>
  ),
);
Select.displayName = 'Select';

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className = '', ...rest }, ref) => (
    <textarea ref={ref} {...rest} className={[CONTROL, className].join(' ')} />
  ),
);
Textarea.displayName = 'Textarea';

interface FieldProps {
  label?: string;
  hint?: string;
  htmlFor?: string;
  children: React.ReactNode;
  className?: string;
}

/** Labelled form row (the design's `.field`): label above the control + optional hint. */
export default function Field({ label, hint, htmlFor, children, className = '' }: FieldProps) {
  return (
    <div className={['flex flex-col gap-[7px]', className].join(' ')}>
      {label && (
        <label htmlFor={htmlFor} className="text-[13px] font-semibold text-ink-2">
          {label}
        </label>
      )}
      {children}
      {hint && <span className="text-[12px] text-muted">{hint}</span>}
    </div>
  );
}
