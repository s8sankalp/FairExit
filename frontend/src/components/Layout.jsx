import { Link, Outlet, useNavigate } from 'react-router-dom';
import { logout } from '../api.js';

export default function Layout() {
  const navigate = useNavigate();
  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">FairExit</Link>
        <nav>
          <Link to="/">My homes</Link>
          <Link to="/account">Account</Link>
          <button
            className="link"
            onClick={async () => {
              await logout();
              navigate('/login');
            }}
          >
            Log out
          </button>
        </nav>
      </header>
      <main className="page">
        <Outlet />
      </main>
      <footer className="footer">FairExit gives information, not legal advice.</footer>
    </>
  );
}
