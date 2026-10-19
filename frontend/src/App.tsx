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
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Shell />
          </RequireAuth>
        }
      >
        <Route index element={<HomePage />} />
        {(
          [
            ['month', <MonthPage />],
            ['products', <ProductsPage />],
            ['clients', <ClientsPage />],
            ['clients/:id', <ClientProfilePage />],
            ['setup/org', <OrgPage />],
            ['setup/partners', <PartnersPage />],
            ['setup/platforms', <PlatformsAndTaxesPage />],
            ['setup/uploads', <UploadsPage />],
            ['setup/import', <ImportPage />],
            ['admin', <AdminPage />],
          ] as const
        ).map(([path, page]) => (
          <Route key={path} path={path} element={<RequireAuth roles={MASTER_READ}>{page}</RequireAuth>} />
        ))}
        {/* Old addresses: forwarded to their new panel, filters kept. */}
        <Route path="fd" element={<Moved to="/products" tab="FD" />} />
        <Route path="bonds" element={<Moved to="/products" tab="BOND" />} />
        <Route path="aif" element={<Moved to="/products" tab="AIF" />} />
        <Route path="setup/product-taxes" element={<Moved to="/setup/platforms" tab="taxes" />} />
        <Route path="users" element={<Moved to="/admin" tab="users" />} />
        <Route path="audit" element={<Moved to="/admin" tab="audit" />} />
        <Route path="activity" element={<Moved to="/admin" tab="activity" />} />
        <Route
          path="setup/load-clients"
          element={
            <RequireAuth roles={MASTER_WRITE}>
              <LoadClientsPage />
            </RequireAuth>
          }
        />
        <Route
          path="setup/load-shares"
          element={
            <RequireAuth roles={MASTER_WRITE}>
              <LoadSharesPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<h1>Page not found</h1>} />
      </Route>
    </Routes>
  );
}
