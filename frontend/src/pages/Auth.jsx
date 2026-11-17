import { useState } from 'react';
import { Link } from 'react-router-dom';
import { login, register } from '../api.js';
import { ErrorBox } from '../components/ui.jsx';

function AuthCard({ title, children, footer }) {
  return (
    <div className="auth">
      <div className="card">
        <h1 className="brand-big">FairExit</h1>
        <p className="muted">Get a fair security deposit back.</p>
        <h2>{title}</h2>
        {children}
        <p className="muted small">{footer}</p>
      </div>
    </div>
  );
}

export function Login() {
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(form.email, form.password); // App switches to the home page by itself
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <AuthCard title="Log in" footer={<>New here? <Link to="/register">Create an account</Link></>}>
      <form onSubmit={submit} className="stack">
        <label>Email<input type="email" required value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
        <label>Password<input type="password" required value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
        <ErrorBox error={error} />
        <button disabled={busy}>{busy ? 'Logging in...' : 'Log in'}</button>
      </form>
    </AuthCard>
  );
}

export function Register() {
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await register(form.name, form.email, form.password);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <AuthCard title="Create an account" footer={<>Have an account? <Link to="/login">Log in</Link></>}>
      <form onSubmit={submit} className="stack">
        <label>Your name<input required maxLength={100} value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
        <label>Email<input type="email" required value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
        <label>Password (8 or more characters)<input type="password" required minLength={8} maxLength={72}
          value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
        <ErrorBox error={error} />
        <button disabled={busy}>{busy ? 'Creating...' : 'Create account'}</button>
      </form>
    </AuthCard>
  );
}
