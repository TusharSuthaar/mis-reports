import { useEffect, type ReactNode } from 'react';

/**
 * Centred pop-up for long entry forms. Closes with × or Escape only — not by clicking outside — so a half-filled
 * form is not lost by a stray click.
 */
export function Modal({ title, onClose, children, size = 'wide' }: { title: string; onClose: () => void; children: ReactNode; size?: 'wide' | 'narrow' }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = overflow;
    };
  }, [onClose]);

  return (
    <div className="modal-backdrop">
      <div className={size === 'narrow' ? 'modal narrow' : 'modal'} role="dialog" aria-modal="true" aria-label={title}>
        <header>
          <h2>{title}</h2>
          <button type="button" className="link" aria-label="Close" onClick={onClose}>
            ×
          </button>
        </header>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}
