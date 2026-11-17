import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, newKey } from '../api.js';
import EvidenceEditor from '../components/EvidenceEditor.jsx';
import LinesEditor, { cleanLines, emptyLine } from '../components/LinesEditor.jsx';
import { ActionButton, ErrorBox, Status, useLoad } from '../components/ui.jsx';
import { money, words } from '../format.js';

const toRows = (lines) => lines.map((l) => ({
  item: l.item, category: l.category, amount: String(l.amount), room: l.room ?? '',
}));

/**
 * The confirm screen. While the AI reads the bill (EXTRACTING) it checks again every
 * 2 seconds. Then the tenant edits and confirms the lines, links photos, and runs the audit.
 */
export default function Bill() {
  const { billId } = useParams();
  const navigate = useNavigate();
  const bill = useLoad(() => api.get(`/api/bills/${billId}`), [billId], {
    pollMs: 2000,
    shouldPoll: (b) => b.status === 'EXTRACTING',
  });
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);
  const [evidenceFor, setEvidenceFor] = useState(null);
  const b = bill.data;

  // Fill the editor once the AI's lines (or the saved lines) arrive
  useEffect(() => {
    if (b && b.status !== 'EXTRACTING') setRows(b.lines.length ? toRows(b.lines) : [emptyLine()]);
  }, [b?.status, b?.lines?.length]); // eslint-disable-line react-hooks/exhaustive-deps

  if (bill.error) return <ErrorBox error={bill.error} />;
  if (!b) return <p className="muted">Loading...</p>;

  async function confirm() {
    setError(null);
    try {
      bill.setData(await api.put(`/api/bills/${billId}/lines`, { lines: cleanLines(rows) }));
    } catch (err) {
      setError(err);
    }
  }

  async function audit() {
    setError(null);
    try {
      const result = await api.post(`/api/bills/${billId}/audit`, undefined, { idempotencyKey: newKey() });
      navigate(`/audits/${result.id}`);
    } catch (err) {
      setError(err);
    }
  }

  const editable = ['EXTRACTED', 'EXTRACTION_FAILED', 'CONFIRMED'].includes(b.status);
  const confirmed = b.status === 'CONFIRMED';

  return (
    <>
      <p><Link to={`/tenancies/${b.tenancyId}`}>Back to the home</Link></p>
      <div className="row between">
        <h1>Deduction bill</h1>
        <Status value={b.status} />
      </div>

      {b.status === 'EXTRACTING' && (
        <div className="card">
          <p><strong>Reading your bill...</strong> This usually takes a few seconds.</p>
          <p className="muted small">You do not have to wait: you can type the lines yourself below.</p>
          <button className="secondary"
            onClick={() => bill.setData({ ...b, status: 'EXTRACTION_FAILED', extractionError: null, lines: [] })}>
            Type the lines myself
          </button>
        </div>
      )}
      {b.status === 'EXTRACTION_FAILED' && b.extractionError && <div className="warning">{b.extractionError}</div>}
      {b.status === 'EXTRACTED' && (
        <div className="info">
          Check every line the AI read. Fix anything wrong, then press <strong>Confirm lines</strong>.
        </div>
      )}
      {b.totalMismatch && (
        <div className="warning">
          The message says the total is {money(b.statedTotal)}, but the lines add up to {money(b.total)}.
          A line may be missing or read wrong.
        </div>
      )}

      {rows && (
        <div className="card">
          <LinesEditor lines={rows} onChange={setRows} readOnly={!editable} />
          <ErrorBox error={error} />
          {editable && (
            <div className="row">
              <ActionButton className={confirmed ? 'secondary' : ''} onClick={confirm}>
                {confirmed ? 'Save changes' : 'Confirm lines'}
              </ActionButton>
              {confirmed && <ActionButton onClick={audit}>Run the audit</ActionButton>}
            </div>
          )}
          {b.status === 'AUDITED' && (
            <div className="row">
              <ActionButton onClick={audit}>Run the audit again</ActionButton>
              <span className="muted small">The lines are frozen once audited. Add a new bill to change them.</span>
            </div>
          )}
        </div>
      )}

      {confirmed && (
        <div className="card">
          <h2>Photos as evidence (optional)</h2>
          <p className="muted small">
            Link your move in and move out photos to a charge. A move in photo that already shows the damage
            makes the charge questionable; a photo showing the item was fine at move in lets FairExit say
            honestly when a repair charge is fair.
          </p>
          <ul className="plain">
            {b.lines.map((line) => (
              <li key={line.id}>
                <div className="row between">
                  <span>{line.item} · {words(line.category)} · {money(line.amount)}</span>
                  <button className="link" onClick={() => setEvidenceFor(evidenceFor === line.id ? null : line.id)}>
                    {evidenceFor === line.id ? 'Close' : 'Link photos'}
                  </button>
                </div>
                {evidenceFor === line.id && (
                  <EvidenceEditor billId={billId} tenancyId={b.tenancyId} lineId={line.id}
                    onSaved={() => setEvidenceFor(null)} />
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </>
  );
}
