import { useState } from 'react';
import { indianDigits } from '../format';

/** The register's own revenue figures, kept as they are instead of being worked out (R-79). Strings as typed. */
export interface Given {
  on: boolean;
  gross: string;
  tds: string;
  gst: string;
  invoice: string;
}

const n = (s: string) => Number(s.replace(/,/g, '')) || 0;
const round2 = (x: number) => Math.round((x + Number.EPSILON) * 100) / 100;

/** State for the register-figures block, started from a saved entry when it kept its figures. */
export function useGiven(entry?: { figuresAsGiven: boolean; gross: number; tds: number; gst: number; invoice: number }) {
  return useState<Given>(
    entry?.figuresAsGiven
      ? { on: true, gross: indianDigits(String(entry.gross)), tds: indianDigits(String(entry.tds)), gst: indianDigits(String(entry.gst)), invoice: indianDigits(String(entry.invoice)) }
      : { on: false, gross: '', tds: '', gst: '', invoice: '' },
  );
}

/** What the request carries for the register figures. */
export function givenBody(g: Given) {
  return g.on
    ? { figuresAsGiven: true, givenGross: n(g.gross), givenTds: g.tds === '' ? 0 : n(g.tds), givenGst: g.gst === '' ? 0 : n(g.gst), givenInvoice: g.invoice === '' ? null : n(g.invoice) }
    : { figuresAsGiven: false };
}

/** The figures to show: the register's when ticked (net = gross − TDS, blank invoice = gross + GST), else the worked-out ones. */
export function shownFigures<T extends { gross: number; tds: number; gst: number; net: number; invoice: number }>(worked: T, g: Given): T {
  if (!g.on) return worked;
  const gross = n(g.gross), tds = n(g.tds), gst = n(g.gst);
  return { ...worked, gross, tds, gst, net: round2(gross - tds), invoice: g.invoice === '' ? round2(gross + gst) : n(g.invoice) };
}

export function GivenFiguresFields({ value, onChange }: { value: Given; onChange: (g: Given) => void }) {
  const set = (patch: Partial<Given>) => onChange({ ...value, ...patch });
  return (
    <>
      <label className="check">
        <input type="checkbox" checked={value.on} onChange={(e) => set({ on: e.target.checked })} />
        Use the register&apos;s figures (don&apos;t recalculate)
      </label>
      {value.on && (
        <>
          <label>
            Gross revenue (excl. GST)
            <input value={value.gross} onChange={(e) => set({ gross: indianDigits(e.target.value) })} inputMode="decimal" required />
          </label>
          <label>
            TDS
            <input value={value.tds} onChange={(e) => set({ tds: indianDigits(e.target.value) })} inputMode="decimal" aria-label="Register TDS" />
          </label>
          <label>
            GST
            <input value={value.gst} onChange={(e) => set({ gst: indianDigits(e.target.value) })} inputMode="decimal" aria-label="Register GST" />
          </label>
          <label>
            Invoice amount
            <input value={value.invoice} onChange={(e) => set({ invoice: indianDigits(e.target.value) })} inputMode="decimal" placeholder="gross + GST" aria-label="Register invoice amount" />
          </label>
          <p className="muted">Stored exactly as entered; the END file uses these. Net = gross − TDS.</p>
        </>
      )}
    </>
  );
}
