import { CATEGORIES, money, words } from '../format.js';

export const emptyLine = () => ({ item: '', category: 'OTHER', amount: '', room: '' });

/** Turns the editor's rows into the API's lines, dropping empty rows. */
export function cleanLines(lines) {
  return lines
    .filter((l) => l.item.trim() || l.amount !== '')
    .map((l) => ({ item: l.item.trim(), category: l.category, amount: Number(l.amount), room: l.room.trim() || null }));
}

/** The confirm screen's table: edit, delete or add lines. */
export default function LinesEditor({ lines, onChange, readOnly = false }) {
  const update = (index, field, value) =>
    onChange(lines.map((l, i) => (i === index ? { ...l, [field]: value } : l)));
  const total = lines.reduce((sum, l) => sum + (Number(l.amount) || 0), 0);

  return (
    <div className="table-wrap">
      <table className="lines">
        <thead>
          <tr><th>Charge</th><th>Type</th><th className="num">Amount (₹)</th><th>Room</th>{!readOnly && <th />}</tr>
        </thead>
        <tbody>
          {lines.map((l, i) => (
            <tr key={i}>
              <td>{readOnly ? l.item : (
                <input required maxLength={200} value={l.item} onChange={(e) => update(i, 'item', e.target.value)} />
              )}</td>
              <td>{readOnly ? words(l.category) : (
                <select value={l.category} onChange={(e) => update(i, 'category', e.target.value)}>
                  {CATEGORIES.map((c) => <option key={c} value={c}>{words(c)}</option>)}
                </select>
              )}</td>
              <td className="num">{readOnly ? money(l.amount) : (
                <input required type="number" min="0.01" step="0.01" value={l.amount}
                  onChange={(e) => update(i, 'amount', e.target.value)} />
              )}</td>
              <td>{readOnly ? (l.room || '') : (
                <input maxLength={60} value={l.room ?? ''} onChange={(e) => update(i, 'room', e.target.value)} />
              )}</td>
              {!readOnly && (
                <td>
                  <button type="button" className="link danger" disabled={lines.length === 1}
                    onClick={() => onChange(lines.filter((_, j) => j !== i))}>Remove</button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={2}>
              {!readOnly && (
                <button type="button" className="link" disabled={lines.length >= 50}
                  onClick={() => onChange([...lines, emptyLine()])}>+ Add a line</button>
              )}
            </td>
            <td className="num"><strong>{money(total)}</strong></td>
            <td colSpan={readOnly ? 1 : 2} />
          </tr>
        </tfoot>
      </table>
    </div>
  );
}
