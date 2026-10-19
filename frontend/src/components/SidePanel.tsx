import type { ReactNode } from 'react';
import { Modal } from './Modal';

/**
 * Create/edit forms for masters (org, partners, platforms…). Since the 2026-10 redesign every add/edit opens as the same
 * centred pop-up (narrower than the product forms); × or Escape closes it.
 */
export function SidePanel({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <Modal title={title} onClose={onClose} size="narrow">
      {children}
    </Modal>
  );
}
