import { useEffect, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { getTokens } from './api.js';
import Layout from './components/Layout.jsx';
import Account from './pages/Account.jsx';
import Audit from './pages/Audit.jsx';
import { Login, Register } from './pages/Auth.jsx';
import Bill from './pages/Bill.jsx';
import NewBill from './pages/NewBill.jsx';
import Tenancies from './pages/Tenancies.jsx';
import TenancyDetail from './pages/TenancyDetail.jsx';

/** True while tokens are stored; updates when api.js logs in or out. */
function useLoggedIn() {
  const [loggedIn, setLoggedIn] = useState(() => Boolean(getTokens()));
  useEffect(() => {
    const update = () => setLoggedIn(Boolean(getTokens()));
    window.addEventListener('fairexit-auth', update);
    window.addEventListener('storage', update); // another tab logged in or out
    return () => {
      window.removeEventListener('fairexit-auth', update);
      window.removeEventListener('storage', update);
    };
  }, []);
  return loggedIn;
}

export default function App() {
  const loggedIn = useLoggedIn();
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={loggedIn ? <Navigate to="/" replace /> : <Login />} />
        <Route path="/register" element={loggedIn ? <Navigate to="/" replace /> : <Register />} />
        <Route element={loggedIn ? <Layout /> : <Navigate to="/login" replace />}>
          <Route path="/" element={<Tenancies />} />
          <Route path="/tenancies/:tenancyId" element={<TenancyDetail />} />
          <Route path="/tenancies/:tenancyId/bills/new" element={<NewBill />} />
          <Route path="/bills/:billId" element={<Bill />} />
          <Route path="/audits/:auditId" element={<Audit />} />
          <Route path="/account" element={<Account />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
