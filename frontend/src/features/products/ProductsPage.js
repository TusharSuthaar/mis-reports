import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PAYMENT_LABEL } from '../../api/types';
import { useLoad } from '../../api/useLoad';
import { useAuth } from '../../app/auth';
import { canEditMasters } from '../../app/permissions';
import { Icon } from '../../components/Icon';
import { ListFilters, listPath, useFilterQuery } from '../../components/ListFilters';
import { Tabs, useTab } from '../../components/Tabs';
import { Amount } from '../../components/Amount';
import { AifPage } from '../aif/AifPage';
import { BondsPage } from '../bonds/BondsPage';
import { FdPage } from '../fd/FdPage';
import { formatMonth } from '../../format';
import { GiftPage } from '../gift/GiftPage';
import { InsurancePage } from '../insurance/InsurancePage';
import { LoansPage } from '../loans/LoansPage';
import { MfPage } from '../mf/MfPage';
import { NpsPage } from '../nps/NpsPage';
import { P2pPage } from '../p2p/P2pPage';
import { EquityPage } from '../equity/EquityPage';
import { PmsPage } from '../pms/PmsPage';
import { KIND_LABEL, PreIpoPage } from '../preipo/PreIpoPage';
const TABS = [
    { key: 'ALL', label: 'All products' },
    { key: 'MF', label: 'MF' },
    { key: 'PREIPO', label: 'Unlisted' },
    { key: 'FD', label: 'FD' },
    { key: 'BOND', label: 'Bonds' },
    { key: 'AIF', label: 'AIF' },
    { key: 'LOAN', label: 'Loans' },
    { key: 'GIFT', label: 'GIFT CITY' },
    { key: 'PMS', label: 'PMS' },
    { key: 'NPS', label: 'NPS' },
    { key: 'P2P', label: 'P2P' },
    { key: 'INS', label: 'Insurance' },
    { key: 'EQUITY', label: 'Equity' },
];
const PRODUCT_NAME = { PREIPO: 'Unlisted', FD: 'FD', BOND: 'Bond', AIF: 'AIF', LOAN: 'Loan', GIFT: 'GIFT City', PMS: 'PMS', NPS: 'NPS', P2P: 'P2P', INS: 'Insurance' };
/** Product entries: every product booked by hand on one page — all together, or one product with its own columns. */
export function ProductsPage() {
    const [tab] = useTab(TABS);
    const [params, setParams] = useSearchParams();
    // Switching tab keeps the common filters but drops the product-only ones and any open pop-up.
    const switchTab = (key) => {
        const next = new URLSearchParams(params);
        ['platform', 'platformId', 'lender', 'fund', 'amc', 'pop', 'new', 'open'].forEach((k) => next.delete(k));
        next.set('tab', key);
        setParams(next, { replace: true });
    };
    return (_jsxs("section", { children: [_jsxs("div", { className: "page-head", children: [_jsxs("div", { children: [_jsx("h1", { children: "Product entries" }), _jsx("p", { className: "muted", style: { margin: 0 }, children: "MF and equity brokerage from the monthly uploads, and Unlisted, FD, bonds, AIF, loans, GIFT City, PMS, NPS, P2P and insurance booked by hand. Filters apply across months." })] }), _jsx(NewEntryMenu, { onPick: (p) => {
                            const next = new URLSearchParams(params);
                            ['platform', 'platformId', 'lender', 'fund', 'amc', 'pop', 'open'].forEach((k) => next.delete(k));
                            next.set('tab', p);
                            next.set('new', '1');
                            setParams(next, { replace: true });
                        } })] }), _jsx(Tabs, { tabs: TABS, value: tab, onChange: switchTab }), tab === 'ALL' && _jsx(AllProducts, { onOpen: (product, id) => {
                    const next = new URLSearchParams(params);
                    next.set('tab', product);
                    next.set('open', String(id));
                    setParams(next, { replace: true });
                } }), tab === 'MF' && _jsx(MfPage, {}), tab === 'PREIPO' && _jsx(PreIpoPage, { embedded: true }), tab === 'FD' && _jsx(FdPage, { embedded: true }), tab === 'BOND' && _jsx(BondsPage, { embedded: true }), tab === 'AIF' && _jsx(AifPage, { embedded: true }), tab === 'LOAN' && _jsx(LoansPage, { embedded: true }), tab === 'GIFT' && _jsx(GiftPage, { embedded: true }), tab === 'PMS' && _jsx(PmsPage, { embedded: true }), tab === 'NPS' && _jsx(NpsPage, { embedded: true }), tab === 'P2P' && _jsx(P2pPage, { embedded: true }), tab === 'INS' && _jsx(InsurancePage, { embedded: true }), tab === 'EQUITY' && _jsx(EquityPage, {})] }));
}
function NewEntryMenu({ onPick }) {
    const { user } = useAuth();
    const [open, setOpen] = useState(false);
    const ref = useRef(null);
    useEffect(() => {
        const close = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false);
        document.addEventListener('mousedown', close);
        return () => document.removeEventListener('mousedown', close);
    }, []);
    if (!canEditMasters(user?.role))
        return null;
    return (_jsxs("div", { className: "menu-wrap", ref: ref, children: [_jsx("button", { type: "button", onClick: () => setOpen(!open), "aria-haspopup": "menu", "aria-expanded": open, children: _jsxs("span", { style: { display: 'inline-flex', alignItems: 'center', gap: 6 }, children: [_jsx(Icon, { name: "plus", size: 16 }), " New entry \u25BE"] }) }), open && (_jsx("div", { className: "menu", role: "menu", children: ['PREIPO', 'FD', 'BOND', 'AIF', 'LOAN', 'GIFT', 'PMS', 'NPS', 'P2P', 'INS'].map((p) => (_jsxs("button", { type: "button", role: "menuitem", onClick: () => { setOpen(false); onPick(p); }, children: ["New ", PRODUCT_NAME[p]] }, p))) }))] }));
}
/** All products in one list (newest first), with the same filters; a row opens the entry in its product tab. */
function AllProducts({ onOpen }) {
    const query = useFilterQuery();
    const fd = useLoad(listPath('/api/fd', query));
    const bonds = useLoad(listPath('/api/bonds', query));
    const aif = useLoad(listPath('/api/aif', query));
    const loans = useLoad(listPath('/api/loans', query));
    const gifts = useLoad(listPath('/api/gift', query));
    const pms = useLoad(listPath('/api/pms', query));
    const nps = useLoad(listPath('/api/nps', query));
    const p2p = useLoad(listPath('/api/p2p', query));
    const preIpo = useLoad(listPath('/api/preipo', query));
    const ins = useLoad(listPath('/api/insurance', query));
    const rows = [
        ...(preIpo.data ?? []).map((e) => ({ product: 'PREIPO', id: e.id, month: e.month, client: e.clientName, item: `${KIND_LABEL[e.kind]} · ${e.scripName}`, rm: e.rmName, amount: e.amount, gross: e.gross, invoice: e.invoice, payment: e.billingStatus === 'PAYMENT_RECEIVED' || e.billingStatus === 'PAID_BY_CLIENT' ? 'RECEIVED' : 'PENDING', cancelled: e.status === 'CANCELLED' })),
        ...(fd.data ?? []).map((e) => ({ product: 'FD', id: e.id, month: e.month, client: e.clientName, item: `${e.fdName}${e.fdNo ? ` #${e.fdNo}` : ''}`, rm: e.rmName, amount: e.amount, gross: e.gross, invoice: e.invoice, payment: e.paymentStatus, cancelled: e.status === 'CANCELLED' })),
        ...(bonds.data ?? []).map((e) => ({ product: 'BOND', id: e.id, month: e.month, client: e.clientName, item: e.bondName, rm: e.rmName, amount: e.consideration, gross: e.gross, invoice: e.invoice, payment: e.paymentStatus, cancelled: e.status === 'CANCELLED' })),
        ...(aif.data ?? []).map((e) => ({ product: 'AIF', id: e.id, month: e.month, client: e.clientName, item: e.fundName, rm: e.rmName, amount: e.commitment, gross: e.gross, invoice: e.invoice, payment: e.paymentStatus, cancelled: e.status === 'CANCELLED' })),
        ...(loans.data ?? []).map((e) => ({ product: 'LOAN', id: e.id, month: e.month, client: e.clientName, item: `${e.lender} ${e.loanType}${e.payoutKind === 'TRAIL' ? ' (trail)' : ''}`, rm: e.rmName, amount: e.sanctionedAmount, gross: e.gross, invoice: e.invoice, payment: e.paymentStatus, cancelled: e.status === 'CANCELLED' })),
        ...(gifts.data ?? []).map((e) => ({ product: 'GIFT', id: e.id, month: e.month, client: e.clientName, item: e.fund, rm: e.rmName, amount: e.amountInr ?? 0, gross: e.gross, invoice: e.invoice, payment: e.paymentStatus, cancelled: e.status === 'CANCELLED' })),
        ...(pms.data ?? []).map((e) => ({ product: 'PMS', id: e.id, month: e.month, client: e.clientName, item: `${e.amc} · ${e.strategy}${e.holdingStatus === 'REDEEMED' ? ' (redeemed)' : ''}`, rm: e.rmName, amount: e.amount, gross: 0, invoice: 0, payment: null, cancelled: e.status === 'CANCELLED' })),
        ...(nps.data ?? []).map((e) => ({ product: 'NPS', id: e.id, month: e.month, client: e.clientName, item: e.pop, rm: e.rmName, amount: e.amount, gross: e.revenue, invoice: e.invoice, payment: e.revenue > 0 ? (e.receivedOn ? 'RECEIVED' : 'PENDING') : null, cancelled: e.status === 'CANCELLED' })),
        ...(p2p.data ?? []).map((e) => ({ product: 'P2P', id: e.id, month: e.month, client: e.clientName, item: `${e.platform} · ${e.scheme}`, rm: e.rmName, amount: e.amount ?? 0, gross: e.gross, invoice: e.invoice, payment: e.gross > 0 ? (e.receivedOn ? 'RECEIVED' : 'PENDING') : null, cancelled: e.status === 'CANCELLED' })),
        ...(ins.data ?? []).map((e) => ({ product: 'INS', id: e.id, month: e.month, client: e.clientName, item: `${e.insurer} · ${e.productName}`, rm: e.rmName, amount: e.netPremium, gross: e.expectedComm, invoice: e.expectedComm, payment: null, cancelled: e.status === 'CANCELLED' })),
    ].sort((a, b) => b.month.localeCompare(a.month) || a.client.localeCompare(b.client));
    const active = rows.filter((r) => !r.cancelled);
    const byProduct = (p) => active.filter((r) => r.product === p).reduce((s, r) => s + r.gross, 0);
    const loading = !fd.data || !bonds.data || !aif.data || !loans.data || !gifts.data || !pms.data || !nps.data || !p2p.data || !preIpo.data || !ins.data;
    return (_jsxs(_Fragment, { children: [_jsx(ListFilters, { searchHint: "Client, PAN, code, Unlisted scrip, FD / bond / fund name, ISIN, lender, GIFT City fund, PMS AMC or strategy, NPS POP, P2P platform, insurer or policy no." }), !loading && (_jsx("div", { className: "cards", children: ['PREIPO', 'FD', 'BOND', 'AIF', 'LOAN', 'GIFT'].map((p) => (_jsxs("div", { className: "stat-card", children: [_jsxs("span", { className: "label", children: [PRODUCT_NAME[p], " gross"] }), _jsx("span", { className: "value", children: _jsx(Amount, { value: byProduct(p) }) }), _jsxs("span", { className: "hint", children: [active.filter((r) => r.product === p).length, " entries"] })] }, p))) })), (fd.error || bonds.error || aif.error || loans.error || gifts.error || pms.error || nps.error || p2p.error || preIpo.error || ins.error) && _jsx("p", { className: "error", children: fd.error ?? bonds.error ?? aif.error ?? loans.error ?? gifts.error ?? pms.error ?? nps.error ?? p2p.error ?? preIpo.error ?? ins.error }), !loading && rows.length === 0 && _jsx("div", { className: "empty", children: "No entries match. Use \u201CNew entry\u201D to add one." }), rows.length > 0 && (_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [_jsx("th", { children: "Product" }), _jsx("th", { children: "Month" }), _jsx("th", { children: "Client" }), _jsx("th", { children: "FD / bond / fund / loan" }), _jsx("th", { children: "RM" }), _jsx("th", { className: "num", children: "Amount" }), _jsx("th", { className: "num", children: "Gross" }), _jsx("th", { className: "num", children: "Invoice" }), _jsx("th", { children: "Payment" })] }) }), _jsxs("tbody", { children: [rows.map((r) => (_jsxs("tr", { className: r.cancelled ? 'muted' : undefined, children: [_jsx("td", { children: _jsx("span", { className: "badge", children: PRODUCT_NAME[r.product] }) }), _jsx("td", { children: formatMonth(r.month) }), _jsxs("td", { children: [_jsx("button", { type: "button", className: "cell-link", onClick: () => onOpen(r.product, r.id), children: r.client }), r.cancelled && _jsx("span", { className: "badge", children: "cancelled" })] }), _jsx("td", { children: r.item }), _jsx("td", { children: r.rm }), _jsx("td", { className: "num", children: _jsx(Amount, { value: r.amount }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: r.gross }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: r.invoice }) }), _jsx("td", { children: r.payment == null ? _jsx("span", { className: "muted", children: "entry only" }) : _jsx("span", { className: r.payment === 'RECEIVED' ? 'badge ok' : 'badge warn', children: PAYMENT_LABEL[r.payment] }) })] }, `${r.product}-${r.id}`))), _jsxs("tr", { className: "total", children: [_jsxs("td", { colSpan: 6, children: ["Total (", active.length, " entries)"] }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, r) => s + r.gross, 0) }) }), _jsx("td", { className: "num", children: _jsx(Amount, { value: active.reduce((s, r) => s + r.invoice, 0) }) }), _jsx("td", {})] })] })] }))] }));
}
