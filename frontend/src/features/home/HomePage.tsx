import { useAuth } from '../../app/auth';

export function HomePage() {
  const { user } = useAuth();
  return (
    <section>
      <h1>Welcome, {user?.displayName}</h1>
      <p className="muted">Signed in as {user?.username} with role {user?.role}.</p>
    </section>
  );
}
