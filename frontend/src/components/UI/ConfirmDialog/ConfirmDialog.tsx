import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import Button from '../Button/Button';

interface ConfirmDialogProps {
  title?: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** When true the confirm button renders in the destructive accent style. */
  destructive?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Centered confirmation modal (the design's `ConfirmDialog`). Rendered through a
 * portal onto a fixed full-screen overlay so it always sits above page content —
 * the prototype noted a past bug where it rendered inline. Backdrop click and
 * Escape both cancel.
 */
export default function ConfirmDialog({
  title = 'Are you sure?',
  message,
  confirmLabel = 'Delete',
  cancelLabel = 'Cancel',
  destructive = true,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel]);

  return createPortal(
    <div
      className="fixed inset-0 z-[9999] grid place-items-center bg-[rgba(0,0,0,0.35)] p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
      role="dialog"
      aria-modal="true"
    >
      <div className="reveal w-full max-w-[380px] rounded-md bg-paper p-7 shadow-pop">
        <h3 className="font-serif text-xl mt-0 mb-2.5">{title}</h3>
        <p className="text-muted text-sm leading-relaxed mb-6">{message}</p>
        <div className="flex justify-end gap-2.5">
          <Button variant="ghost" onClick={onCancel}>
            {cancelLabel}
          </Button>
          <Button variant={destructive ? 'accent' : 'primary'} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
