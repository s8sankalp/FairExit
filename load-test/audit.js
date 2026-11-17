// k6 load test for the Deduction Audit (spec: 100 requests per second; PRD: audit p95 under 300 ms).
//
// Run against the docker-compose stack, with the per user rate limit raised so the test
// measures the app rather than the limiter (set RATE_LIMIT_CAPACITY=1000000 in .env first):
//
//   docker compose --profile loadtest run --rm k6
//
// k6 prints p95 for every request type at the end. Thresholds make the run fail when the
// targets are missed, so it can gate CI.

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = Number(__ENV.RATE || 100);       // requests per second
const DURATION = __ENV.DURATION || '1m';

export const options = {
  scenarios: {
    audits: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{name:audit}': ['p(95)<300'],
    'http_req_duration{name:get_audit}': ['p(95)<150'],
  },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function post(path, body, token, name) {
  const headers = token ? { ...JSON_HEADERS, Authorization: `Bearer ${token}` } : JSON_HEADERS;
  return http.post(`${BASE}${path}`, JSON.stringify(body), { headers, tags: { name } });
}

// Runs once: one user, one tenancy, one bill shaped like the research report's worked example
export function setup() {
  const email = `load-${Date.now()}@example.com`;
  post('/api/auth/register', { email, password: 'password123', name: 'Load Test' }, null, 'register');
  const token = post('/api/auth/login', { email, password: 'password123' }, null, 'login').json('accessToken');

  const tenancy = post('/api/tenancies', {
    city: 'Bengaluru', state: 'KA', flatSize: 'TWO_BHK', rent: 30000, deposit: 150000,
    startDate: '2024-06-01', endDate: '2026-06-01', noticeDays: 30,
  }, token, 'tenancy').json('id');

  const bill = post(`/api/tenancies/${tenancy}/bills`, { lines: [
    { item: 'Full flat repainting', category: 'PAINTING', amount: 55000 },
    { item: 'Lift usage', category: 'SOCIETY_CHARGE', amount: 2000 },
    { item: 'Deep cleaning', category: 'CLEANING', amount: 8000 },
    { item: 'Broken bathroom tap', category: 'REPAIR', amount: 1500, room: 'Bathroom' },
    { item: 'Pending electricity bill', category: 'UTILITY', amount: 3200 },
  ] }, token, 'bill').json('id');

  return { token, bill };
}

// Each iteration: run a full audit (rules + database write), then read it back
export default function (data) {
  const audit = post(`/api/bills/${data.bill}/audit`, {}, data.token, 'audit');
  check(audit, { 'audit is 201': (r) => r.status === 201 });

  const id = audit.status === 201 ? audit.json('id') : null;
  if (id) {
    const read = http.get(`${BASE}/api/audits/${id}`, {
      headers: { Authorization: `Bearer ${data.token}` }, tags: { name: 'get_audit' },
    });
    check(read, { 'read is 200': (r) => r.status === 200 });
  }
}
