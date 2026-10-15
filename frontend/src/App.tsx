import { Route, Routes } from 'react-router-dom';
import { RequireAuth } from './app/auth';
import { MASTER_READ, MASTER_WRITE } from './app/permissions';
import { Shell } from './app/Shell';
import { AuditPage } from './features/audit/AuditPage';
import { ClientsPage } from './features/clients/ClientsPage';
import { MonthPage } from './features/month/MonthPage';
import { HomePage } from './features/home/HomePage';
import { LoginPage } from './features/login/LoginPage';
import { ImportPage } from './features/setup/ImportPage';
import { LoadClientsPage } from './features/setup/LoadClientsPage';
import { LoadSharesPage } from './features/setup/LoadSharesPage';
import { OrgPage } from './features/setup/OrgPage';
import { PartnersPage } from './features/setup/PartnersPage';
import { UsersPage } from './features/users/UsersPage';

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
        <Route
          path="users"
          element={
            <RequireAuth roles={['ADMIN']}>
              <UsersPage />
            </RequireAuth>
          }
        />
        <Route
          path="audit"
          element={
            <RequireAuth roles={['ADMIN']}>
              <AuditPage />
            </RequireAuth>
          }
        />
        {(
          [
            ['month', <MonthPage />],
            ['clients', <ClientsPage />],
            ['setup/org', <OrgPage />],
            ['setup/partners', <PartnersPage />],
            ['setup/import', <ImportPage />],
          ] as const
        ).map(([path, page]) => (
          <Route key={path} path={path} element={<RequireAuth roles={MASTER_READ}>{page}</RequireAuth>} />
        ))}
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
