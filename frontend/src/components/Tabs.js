import { jsx as _jsx } from "react/jsx-runtime";
import { useSearchParams } from 'react-router-dom';
/** The selected tab lives in the page address (?tab=…), so a refresh or a shared link opens the same tab. */
export function useTab(tabs, fallback) {
    const [params, setParams] = useSearchParams();
    const current = params.get('tab');
    const tab = tabs.some((t) => t.key === current) ? current : (fallback ?? tabs[0].key);
    const select = (key) => {
        const next = new URLSearchParams(params);
        next.set('tab', key);
        setParams(next, { replace: true });
    };
    return [tab, select];
}
export function Tabs({ tabs, value, onChange }) {
    return (_jsx("div", { className: "tabs", role: "tablist", children: tabs.map((t) => (_jsx("button", { type: "button", role: "tab", "aria-selected": t.key === value, className: t.key === value ? 'tab active' : 'tab', onClick: () => onChange(t.key), children: t.label }, t.key))) }));
}
