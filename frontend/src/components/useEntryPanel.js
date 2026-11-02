import { useSearchParams } from 'react-router-dom';
/** Which entry pop-up is open ("new" or an entry id) — kept in the address (?new=1 / ?open=12) so links can open one. */
export function useEntryPanel() {
    const [params, setParams] = useSearchParams();
    const open = params.get('open');
    const panel = params.get('new') ? 'new' : open ? Number(open) : null;
    const set = (p) => {
        const next = new URLSearchParams(params);
        next.delete('new');
        next.delete('open');
        if (p === 'new')
            next.set('new', '1');
        else if (p != null)
            next.set('open', String(p));
        setParams(next, { replace: true });
    };
    return [panel, set];
}
