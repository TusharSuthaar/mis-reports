import { jsxs as _jsxs, jsx as _jsx, Fragment as _Fragment } from "react/jsx-runtime";
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { PAGE_ROWS, useTableView } from './useTableView';
function List({ rows }) {
    const view = useTableView(rows, { name: (r) => r.name, amount: (r) => r.amount });
    return (_jsxs(_Fragment, { children: [_jsxs("table", { children: [_jsx("thead", { children: _jsxs("tr", { children: [view.th('name', 'Client'), view.th('amount', 'Amount', 'num')] }) }), _jsx("tbody", { children: view.rows.map((r) => (_jsx("tr", { children: _jsx("td", { children: r.name }) }, r.name))) })] }), view.more] }));
}
const names = () => screen.getAllByRole('row').slice(1).map((r) => r.textContent);
describe('useTableView', () => {
    it('sorts up, down, then back to the list order, with empty amounts last', async () => {
        render(_jsx(List, { rows: [{ name: 'Branch 10', amount: 5 }, { name: 'branch 2', amount: null }, { name: 'Asha', amount: 20 }] }));
        const amount = screen.getByRole('button', { name: /Amount/ });
        await userEvent.click(amount);
        expect(names()).toEqual(['Branch 10', 'Asha', 'branch 2']);
        await userEvent.click(amount);
        expect(names()).toEqual(['Asha', 'Branch 10', 'branch 2']);
        await userEvent.click(amount);
        expect(names()).toEqual(['Branch 10', 'branch 2', 'Asha']);
        await userEvent.click(screen.getByRole('button', { name: /Client/ }));
        expect(names()).toEqual(['Asha', 'branch 2', 'Branch 10']);
    });
    it('shows the first rows of a long list and the rest on request', async () => {
        render(_jsx(List, { rows: Array.from({ length: PAGE_ROWS + 5 }, (_, i) => ({ name: `C${i}`, amount: i })) }));
        expect(screen.getAllByRole('row')).toHaveLength(PAGE_ROWS + 1);
        await userEvent.click(screen.getByRole('button', { name: `Show all ${PAGE_ROWS + 5}` }));
        expect(screen.getAllByRole('row')).toHaveLength(PAGE_ROWS + 6);
    });
});
