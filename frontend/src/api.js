// The only file that talks to the backend. Plain fetch, no HTTP library.
//
// Tokens: the access token (15 minutes) is sent on every call. When a call gets 401,
// the refresh token is swapped for a new pair once and the call is repeated.
// Both are kept in localStorage so a page reload keeps you logged in. (An httpOnly
// cookie would be safer against XSS; that needs a backend change and is noted in the README.)

const TOKENS_KEY = 'fairexit.tokens';

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export function getTokens() {
  try {
    return JSON.parse(localStorage.getItem(TOKENS_KEY));
  } catch {
    return null;
  }
}

export function setTokens(tokens) {
  if (tokens) {
    localStorage.setItem(TOKENS_KEY, JSON.stringify(tokens));
  } else {
    localStorage.removeItem(TOKENS_KEY);
  }
  window.dispatchEvent(new Event('fairexit-auth'));
}

let refreshing = null; // one refresh at a time, even if several calls get 401 together

async function refreshTokens() {
  const tokens = getTokens();
  if (!tokens?.refreshToken) return false;
  refreshing ??= fetch('/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: tokens.refreshToken }),
  })
    .then(async (res) => {
      if (!res.ok) return false;
      setTokens(await res.json());
      return true;
    })
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

async function readError(res) {
  try {
    const body = await res.json();
    return new ApiError(res.status, body.message || res.statusText);
  } catch {
    return new ApiError(res.status, res.statusText || 'Request failed');
  }
}

/**
 * options.idempotencyKey: sent as Idempotency-Key, so retrying the same action
 * (double click, flaky network) cannot create something twice.
 */
export async function request(method, path, body, options = {}) {
  const send = () => {
    const headers = {};
    const tokens = getTokens();
    if (tokens?.accessToken) headers.Authorization = `Bearer ${tokens.accessToken}`;
    if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey;
    let payload;
    if (options.raw !== undefined) {
      headers['Content-Type'] = options.contentType;
      payload = options.raw;
    } else if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }
    return fetch(path, { method, headers, body: payload });
  };

  let res = await send();
  if (res.status === 401 && !path.startsWith('/api/auth/') && (await refreshTokens())) {
    res = await send();
  }
  if (res.status === 401 && !path.startsWith('/api/auth/')) {
    setTokens(null); // session over: the app shows the login page
  }
  if (!res.ok) throw await readError(res);
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body, options) => request('POST', path, body, options),
  put: (path, body, options) => request('PUT', path, body, options),
  del: (path, body) => request('DELETE', path, body),
};

/** A fresh key per user action. */
export const newKey = () => crypto.randomUUID();

// ---- auth ----

export async function login(email, password) {
  setTokens(await api.post('/api/auth/login', { email, password }));
}

export async function register(name, email, password) {
  await api.post('/api/auth/register', { name, email, password });
  await login(email, password);
}

export async function logout() {
  const tokens = getTokens();
  if (tokens?.refreshToken) {
    try {
      await api.post('/api/auth/logout', { refreshToken: tokens.refreshToken });
    } catch {
      // logging out locally is what matters
    }
  }
  setTokens(null);
}

// ---- vault upload: ticket, PUT straight to storage, complete ----

const TYPES = { 'image/jpeg': true, 'image/png': true, 'application/pdf': true };

/** meta: { type, phase?, room?, item?, amount? }. Returns the stored document. */
export async function uploadFile(tenancyId, file, meta) {
  if (!TYPES[file.type]) throw new ApiError(415, 'Only JPG, PNG and PDF files can be uploaded');
  if (file.size > 10 * 1024 * 1024) throw new ApiError(413, 'Files can be at most 10 MB');

  const ticket = await api.post(`/api/tenancies/${tenancyId}/uploads`, {
    ...meta,
    fileName: file.name,
    contentType: file.type,
    sizeBytes: file.size,
  });
  const put = await fetch(ticket.uploadUrl, { method: 'PUT', headers: ticket.headers, body: file });
  if (!put.ok) throw new ApiError(put.status, 'The upload to storage failed. Please try again.');
  return api.post(`/api/tenancies/${tenancyId}/uploads/${ticket.documentId}/complete`);
}
