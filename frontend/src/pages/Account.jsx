import { useState } from 'react';
import { api, setTokens } from '../api.js';
import { ErrorBox, useLoad } from '../components/ui.jsx';

export default function Account() {
  const me = useLoad(() => api.get('/api/users/me'), []);
  const [password, setPassword] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState(null);

  async function remove(e) {
    e.preventDefault();
    setError(null);
    try {
      await api.del('/api/users/me', { password });
      setTokens(null);
    } catch (err) {
      setError(err);
    }
  }

  return (
    <>
      <h1>Account</h1>
      <ErrorBox error={me.error} />
      {me.data && (
        <div className="card">
          <p><strong>{me.data.name}</strong></p>
          <p className="muted">{me.data.email}</p>
        </div>
      )}
      <div className="card">
        <h2>Delete my account</h2>
        <p className="muted small">
          This deletes every home, document, photo, bill, audit and evidence pack in your account. It cannot be undone.
        </p>
        {!confirming ? (
          <button className="danger-button" onClick={() => setConfirming(true)}>Delete my account</button>
        ) : (
          <form className="stack narrow" onSubmit={remove}>
            <label>Your password, to confirm
              <input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} />
            </label>
            <ErrorBox error={error} />
            <div className="row">
              <button className="danger-button">Delete everything</button>
              <button type="button" className="secondary" onClick={() => setConfirming(false)}>Cancel</button>
            </div>
          </form>
        )}
      </div>
    </>
  );
}
