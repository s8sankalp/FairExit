import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { ActionButton, CopyButton, ErrorBox, useLoad, Verdict } from '../components/ui.jsx';
import { dateTime, money } from '../format.js';

export default function Audit() {
  const { auditId } = useParams();
  const audit = useLoad(() => api.get(`/api/audits/${auditId}`), [auditId]);
  const replies = useLoad(() => api.get(`/api/audits/${auditId}/replies`), [auditId]);
  const [tone, setTone] = useState('polite');
  const [pack, setPack] = useState(null);
  const [error, setError] = useState(null);

  if (audit.error) return <ErrorBox error={audit.error} />;
  const a = audit.data;
  if (!a) return <p className="muted">Loading...</p>;

  async function buildPack() {
    setError(null);
    try {
      const result = await api.post(`/api/audits/${auditId}/evidence-pack`);
      setPack(result);
      window.open(result.url, '_blank', 'noopener');
    } catch (err) {
      setError(err);
    }
  }

  return (
    <>
      <p><Link to={`/bills/${a.billId}`}>Back to the bill</Link></p>
      <h1>Audit result</h1>
      <p className="muted small">
        {dateTime(a.createdAt)} · {a.tenancyMonths} months of stay · rules {a.rulesVersion}
      </p>
      <div className="notice">{a.disclaimer}</div>

      <div className="totals">
        <div className="total"><span>Claimed</span><strong>{money(a.totals.claimed)}</strong></div>
        <div className="total fair"><span>Fair</span><strong>{money(a.totals.fair)}</strong></div>
        <div className="total questionable"><span>Questionable</span><strong>{money(a.totals.questionable)}</strong></div>
        <div className="total info"><span>Needs info</span><strong>{money(a.totals.needsInfo)}</strong></div>
        <div className="total refund"><span>Ask for back</span><strong>{money(a.totals.suggestedRefund)}</strong></div>
      </div>

      <div className="card">
        <div className="table-wrap">
          <table className="verdicts">
            <thead><tr><th>Charge</th><th className="num">Amount</th><th>Verdict</th><th>Why</th></tr></thead>
            <tbody>
              {a.verdicts.map((v) => (
                <tr key={v.lineId}>
                  <td>{v.item}</td>
                  <td className="num">{money(v.amount)}</td>
                  <td><Verdict value={v.verdict} /></td>
                  <td>
                    {v.reason}
                    <div className="muted small">
                      {v.ruleIds.join(', ')}{v.evidenceIds.length > 0 && ` · ${v.evidenceIds.length} photo(s) used`}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <div className="row between">
          <h2>Reply to the landlord</h2>
          <div className="tabs compact">
            <button className={tone === 'polite' ? 'tab active' : 'tab'} onClick={() => setTone('polite')}>Polite</button>
            <button className={tone === 'firm' ? 'tab active' : 'tab'} onClick={() => setTone('firm')}>Firm</button>
          </div>
        </div>
        <ErrorBox error={replies.error} />
        {replies.data && (
          <>
            <pre className="reply">{replies.data[tone]}</pre>
            <div className="row">
              <CopyButton text={replies.data[tone]} />
              <span className="muted small">
                {tone === 'polite' ? 'Start with this one.' : 'Use this if the polite reply does not work.'}
              </span>
            </div>
          </>
        )}
      </div>

      <div className="card">
        <h2>Evidence pack</h2>
        <p className="muted small">
          One PDF with the tenancy, the clauses used, this audit, every document with its hash, and your
          photos. The vault is checked again when the pack is made. The link works for 7 days, for a lawyer or mediator.
        </p>
        <ErrorBox error={error} />
        <div className="row">
          <ActionButton onClick={buildPack}>Make the evidence pack</ActionButton>
          {pack && (
            <>
              <a href={pack.url} target="_blank" rel="noopener noreferrer">Open the PDF</a>
              <CopyButton text={pack.url} />
              {!pack.chainValid && <span className="danger">The vault check found a problem; see the PDF.</span>}
            </>
          )}
        </div>
      </div>
    </>
  );
}
