import { useEffect, useState } from 'react';
import { words } from '../format.js';

export function ErrorBox({ error }) {
  if (!error) return null;
  return <div className="error" role="alert">{error.message || String(error)}</div>;
}

export function Verdict({ value }) {
  return <span className={`badge verdict-${value}`}>{words(value)}</span>;
}

export function Status({ value }) {
  return <span className={`badge status-${value}`}>{words(value)}</span>;
}

/**
 * Loads data with a function that returns a promise. reload() runs it again.
 * Optional pollMs: runs again every pollMs while shouldPoll(data) is true.
 */
export function useLoad(load, deps, { pollMs, shouldPoll } = {}) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    load()
      .then((data) => !cancelled && setState({ data, error: null, loading: false }))
      .catch((error) => !cancelled && setState((s) => ({ ...s, error, loading: false })));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  useEffect(() => {
    if (!pollMs || !state.data || !shouldPoll?.(state.data)) return undefined;
    const timer = setTimeout(() => setTick((t) => t + 1), pollMs);
    return () => clearTimeout(timer);
  }, [state.data, pollMs, shouldPoll]);

  return { ...state, reload: () => setTick((t) => t + 1), setData: (data) => setState((s) => ({ ...s, data })) };
}

/** A button that shows "Working..." and stops double clicks while its action runs. */
export function ActionButton({ onClick, children, className = '', ...rest }) {
  const [busy, setBusy] = useState(false);
  return (
    <button
      {...rest}
      className={className}
      disabled={busy || rest.disabled}
      onClick={async (e) => {
        setBusy(true);
        try {
          await onClick(e);
        } finally {
          setBusy(false);
        }
      }}
    >
      {busy ? 'Working...' : children}
    </button>
  );
}

export function CopyButton({ text }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      className="secondary"
      onClick={async () => {
        await navigator.clipboard.writeText(text);
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
      }}
    >
      {copied ? 'Copied' : 'Copy'}
    </button>
  );
}
