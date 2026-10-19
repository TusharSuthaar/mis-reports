import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';

export interface TabDef {
  key: string;
  label: ReactNode;
}

/** The selected tab lives in the page address (?tab=…), so a refresh or a shared link opens the same tab. */
export function useTab(tabs: TabDef[], fallback?: string): [string, (key: string) => void] {
  const [params, setParams] = useSearchParams();
  const current = params.get('tab');
  const tab = tabs.some((t) => t.key === current) ? (current as string) : (fallback ?? tabs[0].key);
  const select = (key: string) => {
    const next = new URLSearchParams(params);
    next.set('tab', key);
    setParams(next, { replace: true });
  };
  return [tab, select];
}

export function Tabs({ tabs, value, onChange }: { tabs: TabDef[]; value: string; onChange: (key: string) => void }) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button key={t.key} type="button" role="tab" aria-selected={t.key === value} className={t.key === value ? 'tab active' : 'tab'} onClick={() => onChange(t.key)}>
          {t.label}
        </button>
      ))}
    </div>
  );
}
