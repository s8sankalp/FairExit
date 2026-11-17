import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, newKey, uploadFile } from '../api.js';
import { ActionButton, ErrorBox, Status, useLoad } from '../components/ui.jsx';
import { date, dateTime, FLAT_LABELS, money, words } from '../format.js';

export default function TenancyDetail() {
  const { tenancyId } = useParams();
  const tenancy = useLoad(() => api.get(`/api/tenancies/${tenancyId}`), [tenancyId]);
  const documents = useLoad(() => api.get(`/api/tenancies/${tenancyId}/documents`), [tenancyId]);
  const t = tenancy.data;

  if (tenancy.error) return <ErrorBox error={tenancy.error} />;
  if (!t) return <p className="muted">Loading...</p>;

  return (
    <>
      <p><Link to="/">My homes</Link></p>
      <h1>{FLAT_LABELS[t.flatSize]} in {t.city}</h1>
      <p className="muted">
        {t.stateName} · rent {money(t.rent)} · deposit {money(t.deposit)} ·
        {' '}{date(t.startDate)} to {t.endDate ? date(t.endDate) : 'now'}
        {t.noticeDays !== null && ` · ${t.noticeDays} days notice`}
      </p>
      {t.rulesCoverage === 'GENERAL_GUIDANCE' && (
        <div className="info">FairExit has no rules specific to {t.stateName} yet, so audits here are general guidance.</div>
      )}

      <Bills tenancyId={tenancyId} />
      <Vault tenancyId={tenancyId} documents={documents} />
      <Agreement tenancyId={tenancyId} documents={documents.data || []} />
      <Reminders tenancy={t} />
      <Outcome tenancy={t} documents={documents.data || []} />
    </>
  );
}

function Bills({ tenancyId }) {
  const bills = useLoad(() => api.get(`/api/tenancies/${tenancyId}/bills`), [tenancyId]);
  return (
    <section className="card">
      <div className="row between">
        <h2>Deduction bills</h2>
        <Link className="button" to={`/tenancies/${tenancyId}/bills/new`}>Check a bill</Link>
      </div>
      <ErrorBox error={bills.error} />
      {bills.data?.length === 0 && <p className="muted">When the landlord sends the deductions, check them here.</p>}
      <ul className="plain">
        {bills.data?.map((b) => (
          <li key={b.id} className="row between">
            <Link to={b.latestAuditId ? `/audits/${b.latestAuditId}` : `/bills/${b.id}`}>
              {date(b.createdAt)} · {b.lineCount} lines · {money(b.total)}
            </Link>
            <span className="row">
              {b.latestAuditId && <Link to={`/bills/${b.id}`} className="small">lines</Link>}
              <Status value={b.status} />
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

const TYPES = ['PHOTO', 'AGREEMENT', 'BILL', 'RECEIPT', 'UTILITY_BILL'];

function Vault({ tenancyId, documents }) {
  const [form, setForm] = useState({ type: 'PHOTO', phase: 'MOVE_IN', room: '', item: '', amount: '' });
  const [file, setFile] = useState(null);
  const [error, setError] = useState(null);
  const [verify, setVerify] = useState(null);
  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  async function upload(e) {
    e.preventDefault();
    setError(null);
    try {
      if (!file) throw new Error('Choose a file');
      await uploadFile(tenancyId, file, {
        type: form.type,
        phase: form.type === 'PHOTO' ? form.phase : 'OTHER',
        room: form.room || null,
        item: form.item || null,
        amount: form.type === 'UTILITY_BILL' ? Number(form.amount) : null,
      });
      setFile(null);
      e.target.reset();
      documents.reload();
    } catch (err) {
      setError(err);
    }
  }

  return (
    <section className="card">
      <div className="row between">
        <h2>Evidence vault</h2>
        <ActionButton className="secondary" onClick={async () => setVerify(await api.get(`/api/tenancies/${tenancyId}/verify`))}>
          Verify everything
        </ActionButton>
      </div>
      <p className="muted small">
        Each file gets a SHA-256 hash and the upload time, chained to the file before it, so nothing can be
        changed later without it showing.
      </p>
      {verify && (verify.valid
        ? <div className="ok">All {verify.documentsChecked} documents verified. Nothing has been changed.</div>
        : <div className="warning">
            Problems found: {verify.breaks.map((b) => `${words(b.problem)}${b.chainSeq ? ` (#${b.chainSeq})` : ''}`).join(', ')}
          </div>)}

      <form className="grid3" onSubmit={upload}>
        <label>What is it<select value={form.type} onChange={set('type')}>
          {TYPES.map((tp) => <option key={tp} value={tp}>{words(tp)}</option>)}
        </select></label>
        {form.type === 'PHOTO' && (
          <label>When<select value={form.phase} onChange={set('phase')}>
            <option value="MOVE_IN">Move in</option>
            <option value="MOVE_OUT">Move out</option>
            <option value="OTHER">Other</option>
          </select></label>
        )}
        {form.type === 'UTILITY_BILL' && (
          <label>Amount on the bill (₹)
            <input required type="number" min="0.01" step="0.01" value={form.amount} onChange={set('amount')} />
          </label>
        )}
        <label>Room<input maxLength={60} value={form.room} onChange={set('room')} placeholder="Bedroom 1" /></label>
        <label>Item<input maxLength={100} value={form.item} onChange={set('item')} placeholder="Wall" /></label>
        <label>File<input type="file" accept="image/jpeg,image/png,application/pdf"
          onChange={(e) => setFile(e.target.files[0] || null)} /></label>
        <div className="span3"><ErrorBox error={error} /></div>
        <div className="span3"><button>Upload</button></div>
      </form>

      <ErrorBox error={documents.error} />
      {documents.data?.length > 0 && (
        <div className="table-wrap">
          <table className="docs">
            <thead><tr><th>#</th><th>Document</th><th>Uploaded</th><th>SHA-256</th><th /></tr></thead>
            <tbody>
              {documents.data.map((d) => (
                <tr key={d.id}>
                  <td>{d.chainSeq}</td>
                  <td>
                    {words(d.type)}{d.type === 'PHOTO' && ` · ${words(d.phase)}`}
                    <div className="muted small">{[d.room, d.item].filter(Boolean).join(', ')} {d.fileName}</div>
                  </td>
                  <td className="small">{dateTime(d.uploadedAt)}</td>
                  <td><code title={d.sha256}>{d.sha256.slice(0, 12)}...</code></td>
                  <td>
                    <button className="link" onClick={async () => {
                      const link = await api.get(`/api/documents/${d.id}/download`);
                      window.open(link.url, '_blank', 'noopener');
                    }}>Open</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function Agreement({ tenancyId, documents }) {
  const agreement = useLoad(
    () => api.get(`/api/tenancies/${tenancyId}/agreement`).catch((e) => (e.status === 404 ? null : Promise.reject(e))),
    [tenancyId],
    { pollMs: 2000, shouldPoll: (a) => a?.status === 'EXTRACTING' },
  );
  const [docId, setDocId] = useState('');
  const [error, setError] = useState(null);
  const agreementDocs = documents.filter((d) => d.type === 'AGREEMENT');
  const a = agreement.data;

  async function read() {
    setError(null);
    try {
      agreement.setData(await api.post(`/api/tenancies/${tenancyId}/agreement`, { documentId: docId || agreementDocs[0].id },
        { idempotencyKey: newKey() }));
    } catch (err) {
      setError(err);
    }
  }

  return (
    <section className="card">
      <h2>Rent agreement</h2>
      {agreementDocs.length === 0 ? (
        <p className="muted">Upload your agreement to the vault (type "Agreement"), then read its clauses here.</p>
      ) : (
        <div className="row">
          <select value={docId} onChange={(e) => setDocId(e.target.value)}>
            {agreementDocs.map((d) => <option key={d.id} value={d.id}>{d.fileName} ({date(d.uploadedAt)})</option>)}
          </select>
          <ActionButton onClick={read} disabled={a?.status === 'EXTRACTING'}>Read the clauses</ActionButton>
        </div>
      )}
      <ErrorBox error={error || agreement.error} />
      {a?.status === 'EXTRACTING' && <p>Reading the agreement...</p>}
      {a?.status === 'FAILED' && <div className="warning">{a.error}</div>}
      {a?.status === 'EXTRACTED' && (
        <>
          {a.clauses.length === 0 && <p className="muted">No clauses about the deposit were found.</p>}
          <ul className="clauses">
            {a.clauses.map((c, i) => (
              <li key={i}>
                <strong>{words(c.type)}</strong>
                {c.value?.tenantPays !== undefined && <span> · {c.value.tenantPays ? 'tenant pays' : 'landlord pays'}</span>}
                {c.page && <span className="muted small"> · page {c.page}</span>}
                <blockquote>{c.quote}</blockquote>
                {c.checkedAgainst === 'TRANSCRIPTION' && (
                  <div className="muted small">Read from a photo or scan: please check this quote against your copy.</div>
                )}
              </li>
            ))}
          </ul>
          {a.droppedClauses > 0 && (
            <p className="muted small">
              {a.droppedClauses} suggested clause(s) were left out because their words are not in your agreement.
            </p>
          )}
        </>
      )}
    </section>
  );
}

function Reminders({ tenancy }) {
  const reminders = useLoad(() => api.get(`/api/tenancies/${tenancy.id}/reminders`), [tenancy.id]);
  const [error, setError] = useState(null);

  return (
    <section className="card">
      <div className="row between">
        <h2>Reminders</h2>
        <ActionButton className="secondary" disabled={!tenancy.endDate} onClick={async () => {
          setError(null);
          try {
            reminders.setData(await api.post(`/api/tenancies/${tenancy.id}/reminders`));
          } catch (err) {
            setError(err);
          }
        }}>
          {reminders.data?.length ? 'Plan again' : 'Set reminders'}
        </ActionButton>
      </div>
      {!tenancy.endDate && <p className="muted">Add a move out date to get notice, move out and refund reminders.</p>}
      <ErrorBox error={error || reminders.error} />
      <ul className="plain">
        {reminders.data?.map((r) => (
          <li key={r.id} className="row between">
            <span>{words(r.type)}: {date(r.eventDate)}</span>
            <span className="muted small">
              {r.status === 'SENT' ? `emailed ${dateTime(r.sentAt)}` : `email on ${dateTime(r.dueAt)}`}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

function Outcome({ tenancy, documents }) {
  const outcome = useLoad(
    () => api.get(`/api/tenancies/${tenancy.id}/outcome`).catch((e) => (e.status === 404 ? null : Promise.reject(e))),
    [tenancy.id],
  );
  const [form, setForm] = useState({ amountReturned: '', daysTaken: '', receiptDocumentId: '' });
  const [error, setError] = useState(null);
  const receipts = documents.filter((d) => d.type === 'RECEIPT');

  async function save(e) {
    e.preventDefault();
    setError(null);
    try {
      outcome.setData(await api.post(`/api/tenancies/${tenancy.id}/outcome`, {
        amountReturned: Number(form.amountReturned),
        daysTaken: Number(form.daysTaken),
        receiptDocumentId: form.receiptDocumentId || null,
      }));
    } catch (err) {
      setError(err);
    }
  }

  return (
    <section className="card">
      <h2>What you got back</h2>
      {outcome.data && (
        <div className="ok">
          {money(outcome.data.amountReturned)} of {money(outcome.data.deposit)} ({outcome.data.percentReturned}%)
          {' '}in {outcome.data.daysTaken} days{outcome.data.verified ? ', backed by a receipt' : ''}.
        </div>
      )}
      <form className="grid3" onSubmit={save}>
        <label>Amount returned (₹)<input required type="number" min="0" step="0.01" max={tenancy.deposit}
          value={form.amountReturned} onChange={(e) => setForm({ ...form, amountReturned: e.target.value })} /></label>
        <label>Days it took<input required type="number" min="0" max="730" value={form.daysTaken}
          onChange={(e) => setForm({ ...form, daysTaken: e.target.value })} /></label>
        <label>Receipt (optional)<select value={form.receiptDocumentId}
          onChange={(e) => setForm({ ...form, receiptDocumentId: e.target.value })}>
          <option value="">None</option>
          {receipts.map((d) => <option key={d.id} value={d.id}>{d.fileName}</option>)}
        </select></label>
        <div className="span3"><ErrorBox error={error} /></div>
        <div className="span3"><button>{outcome.data ? 'Update' : 'Save'}</button></div>
      </form>
      <p className="muted small">Saving this stops any reminders still waiting.</p>
    </section>
  );
}
