import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client';
import { useLoad } from '../../api/useLoad';
import { useSubmit } from '../../api/useSubmit';
/*
 * What every product entry form (FD, bonds, AIF, loans, GIFT City, P2P, NPS, PMS) has in common: the client, found by
 * PAN or client code or added as new (R-01), their contact details, the month, the RM and the referral / sub-broker.
 */
export const PAN_FORMAT = /^[A-Z]{5}[0-9]{4}[A-Z]$/;
/**
 * Clients that are this PAN or this client code. The search also matches names, so keep only real hits: for a PAN, the
 * PAN (shown in full, or masked ABCDE****F); otherwise the client code.
 */
export function matchClients(items, looked) {
    const q = looked.trim().toUpperCase();
    if (PAN_FORMAT.test(q)) {
        return items.filter((c) => c.pan != null && (c.pan.toUpperCase() === q || (c.pan.includes('*') && c.pan.slice(0, 5) === q.slice(0, 5) && c.pan.slice(-1) === q.slice(-1))));
    }
    return items.filter((c) => c.iwellCode != null && c.iwellCode.toUpperCase() === q);
}
/**
 * The client / month / RM part of an entry form. {@code src}: the entry being edited (or copied); its client is fixed.
 * Otherwise the client is looked up: one match is taken, several are offered, none asks for a new client.
 */
export function useClientAndRm(src, initialMonth) {
    const [month, setMonth] = useState(initialMonth);
    const [rmId, setRmId] = useState(src ? String(src.rmId) : '');
    const [lookup, setLookup] = useState('');
    const [looked, setLooked] = useState('');
    const [client, setClient] = useState(null);
    const [newName, setNewName] = useState('');
    const [newPan, setNewPan] = useState('');
    const [mobile, setMobile] = useState(src?.mobile ?? '');
    const [email, setEmail] = useState(src?.email ?? '');
    const [address, setAddress] = useState(src?.address ?? '');
    const [partnerId, setPartnerId] = useState(src?.partnerId ? String(src.partnerId) : '');
    const monthDay = `${month.slice(0, 7)}-01`;
    const employees = useLoad('/api/employees');
    const partners = useLoad('/api/partners');
    const chart = useLoad(`/api/org?date=${monthDay}`);
    const found = useLoad(!src && looked ? `/api/clients?q=${encodeURIComponent(looked)}&size=20` : null);
    const matches = useMemo(() => (looked && found.data ? matchClients(found.data.items, looked) : null), [found.data, looked]);
    /** After a lookup: the client found, a choice when several match, or the new-client fields when nothing matches. */
    const clientMode = src ? 'found' : !matches ? 'lookup' : matches.length === 0 ? 'new' : client ? 'found' : 'pick';
    const rmLocked = !src && !!client?.rmId;
    const shares = useLoad(client ? `/api/shares?clientIds=${client.id}` : null);
    useEffect(() => {
        if (matches && matches.length === 1)
            setClient(matches[0]);
        if (matches && matches.length === 0 && PAN_FORMAT.test(looked))
            setNewPan(looked);
    }, [matches, looked]);
    useEffect(() => {
        if (!client)
            return;
        if (client.rmId)
            setRmId(String(client.rmId));
        const own = shares.data?.[client.id]?.find((s) => !s.branch);
        if (own && !partnerId)
            setPartnerId(String(own.partnerId));
        // eslint-disable-next-line react-hooks/exhaustive-deps -- fill in the client's partner once, never over one already chosen
    }, [client, shares.data]);
    const findClient = () => {
        setClient(null);
        setLooked(lookup.trim().toUpperCase());
    };
    const place = useMemo(() => {
        for (const r of chart.data?.regions ?? []) {
            for (const b of r.branches)
                if (b.people.some((p) => String(p.id) === rmId))
                    return { region: r.name, rd: r.rd?.name ?? '—', branch: b.name };
        }
        return null;
    }, [chart.data, rmId]);
    const people = (employees.data ?? []).filter((e) => e.status === 'ACTIVE');
    const sharePartners = (partners.data ?? []).filter((p) => p.type === 'SUB_BROKER' || p.type === 'REFERRAL');
    /** The request fields this part of the form fills. */
    const body = () => ({
        clientId: src ? src.clientId : clientMode === 'found' ? client?.id : null,
        newClientName: clientMode === 'new' ? newName : null,
        newClientPan: clientMode === 'new' && newPan ? newPan : null,
        month: monthDay,
        rmId: Number(rmId),
        partnerId: partnerId ? Number(partnerId) : null,
        mobile,
        email,
        address,
    });
    return {
        src, month, setMonth, monthDay, rmId, setRmId, partnerId, setPartnerId, lookup, setLookup, findClient, matches, client, setClient,
        clientMode, rmLocked, newName, setNewName, newPan, setNewPan, mobile, setMobile, email, setEmail, address, setAddress, place,
        people, sharePartners, body,
        /** The client is settled: found, picked or entered as new. */
        ready: clientMode === 'found' || clientMode === 'new',
    };
}
/** The Client fieldset: lookup by PAN or code, a pick among several, a new client, and contact details. */
export function ClientFieldset({ party, radioName, codeHint = 'FC01234', showClientType = false, mobileLabel = 'Mobile No', emailLabel = 'Email Id' }) {
    const { src, lookup, setLookup, findClient, clientMode, matches, client, setClient } = party;
    return (_jsxs("fieldset", { children: [_jsx("legend", { children: "Client" }), src ? (_jsxs("p", { children: [src.clientName, " ", _jsx("span", { className: "mono", children: src.pan }), showClientType && ` · Client type: ${src.clientType}`] })) : (_jsxs("span", { className: "inline-form", children: [_jsxs("label", { children: ["Client PAN or client code", _jsx("input", { value: lookup, onChange: (e) => setLookup(e.target.value.toUpperCase().replace(/\s/g, '')), onKeyDown: (e) => {
                                    if (e.key === 'Enter') {
                                        e.preventDefault();
                                        findClient();
                                    }
                                }, placeholder: `ABCDE1234F or ${codeHint}` })] }), _jsx("button", { type: "button", className: "secondary", disabled: lookup.trim().length < 3, onClick: findClient, children: "Find" })] })), clientMode === 'pick' &&
                (matches ?? []).map((c) => (_jsxs("label", { className: "check", children: [_jsx("input", { type: "radio", name: radioName, checked: client?.id === c.id, onChange: () => setClient(c) }), c.name, " \u00B7 ", _jsx("span", { className: "mono", children: c.pan ?? 'no PAN' }), " \u00B7 ", c.iwellCode ?? 'no code', " \u00B7 ", c.rmName ?? 'no RM'] }, c.id))), !src && client && (_jsxs("p", { className: "ok", children: [client.name, " \u00B7 ", _jsx("span", { className: "mono", children: client.pan ?? 'no PAN' }), " \u00B7 Client type: ", client.iwellCode ?? 'OFFLINE'] })), clientMode === 'new' && (_jsxs(_Fragment, { children: [_jsx("p", { className: "muted", children: "No client has this PAN or code: enter the new client (client type OFFLINE). They are added to Clients with the RM below." }), _jsxs("label", { children: ["Client name", _jsx("input", { value: party.newName, onChange: (e) => party.setNewName(e.target.value), required: true })] }), _jsxs("label", { children: ["PAN", _jsx("input", { value: party.newPan, onChange: (e) => party.setNewPan(e.target.value.toUpperCase().replace(/\s/g, '')), maxLength: 10, placeholder: "ABCDE1234F" })] })] })), party.ready && (_jsxs(_Fragment, { children: [_jsxs("label", { children: [mobileLabel, _jsx("input", { value: party.mobile, onChange: (e) => party.setMobile(e.target.value), inputMode: "tel" })] }), _jsxs("label", { children: [emailLabel, _jsx("input", { type: "email", value: party.email, onChange: (e) => party.setEmail(e.target.value) })] }), _jsxs("label", { children: ["Address", _jsx("textarea", { value: party.address, onChange: (e) => party.setAddress(e.target.value), rows: 2 })] })] }))] }));
}
/**
 * Month, RM (the client's own when it has one) and referral / sub-broker, with the region, RD and branch the RM sits in.
 * {@code children} go after the partner; {@code note} is added to the line under them.
 */
export function MonthRmFieldset({ party, legend = 'Month and RM', partnerLabel = 'Ref / sub-broker', children, note }) {
    const { month, setMonth, rmLocked, client, rmId, setRmId, people, partnerId, setPartnerId, sharePartners, place } = party;
    return (_jsxs("fieldset", { children: [_jsx("legend", { children: legend }), _jsxs("label", { children: ["Month", _jsx("input", { type: "month", value: month.slice(0, 7), onChange: (e) => e.target.value && setMonth(e.target.value), required: true })] }), rmLocked ? (_jsxs("label", { children: ["RM", _jsx("input", { value: client?.rmName ?? '', readOnly: true })] })) : (_jsxs("label", { children: ["RM", _jsxs("select", { value: rmId, onChange: (e) => setRmId(e.target.value), required: true, children: [_jsx("option", { value: "", children: "Choose\u2026" }), people.map((p) => (_jsx("option", { value: p.id, children: p.branchName ? `${p.name} — ${p.branchName}` : p.name }, p.id)))] })] })), _jsxs("label", { children: [partnerLabel, _jsxs("select", { value: partnerId, onChange: (e) => setPartnerId(e.target.value), children: [_jsx("option", { value: "", children: "None" }), sharePartners.map((p) => (_jsxs("option", { value: p.id, children: [p.name, " (", p.type === 'SUB_BROKER' ? 'sub-broker' : 'referral', ")"] }, p.id)))] })] }), children, _jsxs("p", { className: "muted", children: [place ? `Region: ${place.region} · RD: ${place.rd} · Branch: ${place.branch}` : 'Region and RD come from the RM.', rmLocked && ' · The RM comes from the client: change it on the Clients screen.', note] })] }));
}
/** "Cancel this …": asks why, then cancels the entry (it stays, marked cancelled). */
export function CancelEntry({ url, question, label = 'Cancel this entry', onDone }) {
    const { busy, run } = useSubmit();
    return (_jsx("button", { type: "button", className: "link", disabled: busy, onClick: async () => {
            const reason = window.prompt(question);
            if (reason === null)
                return;
            if (await run(() => api(url, { method: 'POST', body: { reason } })))
                onDone();
        }, children: label }));
}
