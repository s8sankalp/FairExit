import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, newKey } from '../api.js';
import { ErrorBox, useLoad } from '../components/ui.jsx';
import { date, FLAT_LABELS, FLAT_SIZES, money, STATES } from '../format.js';

const EMPTY = {
  city: '', state: 'KA', flatSize: 'TWO_BHK', rent: '', deposit: '', startDate: '', endDate: '', noticeDays: '',
};

export default function Tenancies() {
  const tenancies = useLoad(() => api.get('/api/tenancies'), []);
  const [form, setForm] = useState(EMPTY);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  async function create(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.post('/api/tenancies', {
        ...form,
        rent: Number(form.rent),
        deposit: Number(form.deposit),
        endDate: form.endDate || null,
        noticeDays: form.noticeDays === '' ? null : Number(form.noticeDays),
      }, { idempotencyKey: newKey() });
      setForm(EMPTY);
      setShowForm(false);
      tenancies.reload();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="row between">
        <h1>My homes</h1>
        {!showForm && <button onClick={() => setShowForm(true)}>Add a home</button>}
      </div>

      {showForm && (
        <form className="card grid2" onSubmit={create}>
          <label>City<input required maxLength={60} value={form.city} onChange={set('city')} placeholder="Bengaluru" /></label>
          <label>State<select value={form.state} onChange={set('state')}>
            {STATES.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
          </select></label>
          <label>Home<select value={form.flatSize} onChange={set('flatSize')}>
            {FLAT_SIZES.map((s) => <option key={s} value={s}>{FLAT_LABELS[s]}</option>)}
          </select></label>
          <label>Rent a month (₹)<input required type="number" min="1" step="0.01" value={form.rent} onChange={set('rent')} /></label>
          <label>Deposit (₹)<input required type="number" min="1" step="0.01" value={form.deposit} onChange={set('deposit')} /></label>
          <label>Notice period (days)<input type="number" min="0" max="365" value={form.noticeDays} onChange={set('noticeDays')} /></label>
          <label>Moved in<input required type="date" value={form.startDate} onChange={set('startDate')} /></label>
          <label>Moving out (if known)<input type="date" value={form.endDate} onChange={set('endDate')} /></label>
          <div className="span2"><ErrorBox error={error} /></div>
          <div className="row span2">
            <button disabled={busy}>{busy ? 'Saving...' : 'Save home'}</button>
            <button type="button" className="secondary" onClick={() => setShowForm(false)}>Cancel</button>
          </div>
        </form>
      )}

      <ErrorBox error={tenancies.error} />
      {tenancies.data?.length === 0 && !showForm && (
        <div className="card empty">Add the home you rent to start. Then upload your agreement and move in photos.</div>
      )}
      <div className="list">
        {tenancies.data?.map((t) => (
          <Link key={t.id} to={`/tenancies/${t.id}`} className="card tile">
            <div>
              <strong>{FLAT_LABELS[t.flatSize]} in {t.city}</strong>
              <div className="muted small">{t.stateName} · {date(t.startDate)} to {t.endDate ? date(t.endDate) : 'now'}</div>
            </div>
            <div className="right">
              <div>Deposit {money(t.deposit)}</div>
              {t.rulesCoverage === 'GENERAL_GUIDANCE' && <div className="muted small">General guidance</div>}
            </div>
          </Link>
        ))}
      </div>
    </>
  );
}
