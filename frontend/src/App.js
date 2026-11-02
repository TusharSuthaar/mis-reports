import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { Route, Routes } from 'react-router-dom';
import { RequireAuth } from './app/auth';
import { MASTER_READ, MASTER_WRITE } from './app/permissions';
import { Shell } from './app/Shell';
import { ClientProfilePage, ClientsPage } from './features/clients/ClientsPage';
import { MonthPage } from './features/month/MonthPage';
import { HomePage } from './features/home/HomePage';
import { LoginPage } from './features/login/LoginPage';
import { ImportPage } from './features/setup/ImportPage';
import { LoadClientsPage } from './features/setup/LoadClientsPage';
import { LoadSharesPage } from './features/setup/LoadSharesPage';
import { OrgPage } from './features/setup/OrgPage';
import { PartnersPage } from './features/setup/PartnersPage';
import { AdminPage, Moved, PlatformsAndTaxesPage, UploadsPage } from './features/setup/MastersPanels';
import { ProductsPage } from './features/products/ProductsPage';
export function App() {
    return (_jsxs(Routes, { children: [_jsx(Route, { path: "/login", element: _jsx(LoginPage, {}) }), _jsxs(Route, { element: _jsx(RequireAuth, { children: _jsx(Shell, {}) }), children: [_jsx(Route, { index: true, element: _jsx(HomePage, {}) }), [
                        ['month', _jsx(MonthPage, {})],
                        ['products', _jsx(ProductsPage, {})],
                        ['clients', _jsx(ClientsPage, {})],
                        ['clients/:id', _jsx(ClientProfilePage, {})],
                        ['setup/org', _jsx(OrgPage, {})],
                        ['setup/partners', _jsx(PartnersPage, {})],
                        ['setup/platforms', _jsx(PlatformsAndTaxesPage, {})],
                        ['setup/uploads', _jsx(UploadsPage, {})],
                        ['setup/import', _jsx(ImportPage, {})],
                        ['admin', _jsx(AdminPage, {})],
                    ].map(([path, page]) => (_jsx(Route, { path: path, element: _jsx(RequireAuth, { roles: MASTER_READ, children: page }) }, path))), _jsx(Route, { path: "fd", element: _jsx(Moved, { to: "/products", tab: "FD" }) }), _jsx(Route, { path: "bonds", element: _jsx(Moved, { to: "/products", tab: "BOND" }) }), _jsx(Route, { path: "aif", element: _jsx(Moved, { to: "/products", tab: "AIF" }) }), _jsx(Route, { path: "setup/product-taxes", element: _jsx(Moved, { to: "/setup/platforms", tab: "taxes" }) }), _jsx(Route, { path: "users", element: _jsx(Moved, { to: "/admin", tab: "users" }) }), _jsx(Route, { path: "audit", element: _jsx(Moved, { to: "/admin", tab: "audit" }) }), _jsx(Route, { path: "activity", element: _jsx(Moved, { to: "/admin", tab: "activity" }) }), _jsx(Route, { path: "setup/load-clients", element: _jsx(RequireAuth, { roles: MASTER_WRITE, children: _jsx(LoadClientsPage, {}) }) }), _jsx(Route, { path: "setup/load-shares", element: _jsx(RequireAuth, { roles: MASTER_WRITE, children: _jsx(LoadSharesPage, {}) }) }), _jsx(Route, { path: "*", element: _jsx("h1", { children: "Page not found" }) })] })] }));
}
