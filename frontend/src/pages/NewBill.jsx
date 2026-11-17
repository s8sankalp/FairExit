import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, newKey, uploadFile } from '../api.js';
import { ErrorBox } from '../components/ui.jsx';
import LinesEditor, { cleanLines, emptyLine } from '../components/LinesEditor.jsx';

const MODES = [
  ['text', 'Paste the message'],
  ['file', 'Upload a photo or PDF'],
  ['lines', 'Type the lines'],
];

export default function NewBill() {
  const { tenancyId } = useParams();
  const navigate = useNavigate();
  const [mode, setMode] = useState('text');
  const [text, setText] = useState('');
  const [file, setFile] = useState(null);
  const [lines, setLines] = useState([emptyLine()]);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      let body;
      if (mode === 'text') body = { text };
      if (mode === 'lines') body = { lines: cleanLines(lines) };
      if (mode === 'file') {
        if (!file) throw new Error('Choose a photo or PDF of the bill');
        const doc = await uploadFile(tenancyId, file, { type: 'BILL' });
        body = { documentId: doc.id };
      }
      const bill = await api.post(`/api/tenancies/${tenancyId}/bills`, body, { idempotencyKey: newKey() });
      navigate(`/bills/${bill.id}`);
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <>
      <p><Link to={`/tenancies/${tenancyId}`}>Back to the home</Link></p>
      <h1>Check a deduction bill</h1>
      <div className="tabs">
        {MODES.map(([key, label]) => (
          <button key={key} type="button" className={mode === key ? 'tab active' : 'tab'} onClick={() => setMode(key)}>
            {label}
          </button>
        ))}
      </div>
      <form className="card stack" onSubmit={submit}>
        {mode === 'text' && (
          <label>
            The landlord's message (WhatsApp, SMS or email), as it is. Phone numbers, emails and your
            name are hidden before anything is sent to the AI.
            <textarea required rows={10} maxLength={5000} value={text} onChange={(e) => setText(e.target.value)}
              placeholder={'Painting 55,000\nLift usage 2000\nDeep cleaning 8000\nTotal 65000'} />
          </label>
        )}
        {mode === 'file' && (
          <label>
            A photo or PDF of the bill (JPG, PNG or PDF, up to 10 MB). It is stored in your vault with its hash.
            <input type="file" accept="image/jpeg,image/png,application/pdf"
              onChange={(e) => setFile(e.target.files[0] || null)} />
          </label>
        )}
        {mode === 'lines' && <LinesEditor lines={lines} onChange={setLines} />}
        <ErrorBox error={error} />
        <div><button disabled={busy}>{busy ? 'Sending...' : 'Continue'}</button></div>
      </form>
    </>
  );
}
