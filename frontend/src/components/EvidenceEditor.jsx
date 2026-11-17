import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { ActionButton, ErrorBox, useLoad } from './ui.jsx';
import { dateTime, words } from '../format.js';

/** Pick vault photos for one bill line and say what each shows. */
export default function EvidenceEditor({ billId, tenancyId, lineId, onSaved }) {
  const photos = useLoad(async () => {
    const [docs, current] = await Promise.all([
      api.get(`/api/tenancies/${tenancyId}/documents`),
      api.get(`/api/bills/${billId}/lines/${lineId}/evidence`),
    ]);
    return {
      photos: docs.filter((d) => d.type === 'PHOTO' && d.phase !== 'OTHER'),
      current: Object.fromEntries(current.links.map((l) => [l.documentId, l.observation])),
    };
  }, [tenancyId, billId, lineId]);
  const [choice, setChoice] = useState({});
  const [error, setError] = useState(null);

  useEffect(() => {
    if (photos.data) setChoice(photos.data.current);
  }, [photos.data]);

  if (photos.error) return <ErrorBox error={photos.error} />;
  if (!photos.data) return <p className="muted">Loading photos...</p>;
  if (photos.data.photos.length === 0) {
    return <p className="muted small">No move in or move out photos yet. Upload them on the home's page first.</p>;
  }

  async function save() {
    setError(null);
    try {
      const links = Object.entries(choice)
        .filter(([, observation]) => observation)
        .map(([documentId, observation]) => ({ documentId, observation }));
      await api.put(`/api/bills/${billId}/lines/${lineId}/evidence`, { links });
      onSaved();
    } catch (err) {
      setError(err);
    }
  }

  return (
    <div className="evidence">
      {photos.data.photos.map((p) => (
        <div key={p.id} className="row between">
          <span className="small">
            {words(p.phase)} · {p.room || 'no room'}{p.item ? `, ${p.item}` : ''} · {p.fileName} · {dateTime(p.uploadedAt)}
          </span>
          <select value={choice[p.id] || ''} onChange={(e) => setChoice({ ...choice, [p.id]: e.target.value })}>
            <option value="">Not about this charge</option>
            <option value="NO_DAMAGE">Shows no damage</option>
            <option value="DAMAGE_PRESENT">Shows the damage</option>
          </select>
        </div>
      ))}
      <ErrorBox error={error} />
      <ActionButton onClick={save}>Save photos</ActionButton>
    </div>
  );
}
