// Run: npm test  (Node 22.18+ runs the TypeScript module directly by stripping types)
import test from 'node:test';
import assert from 'node:assert/strict';
import { byDescNullsLast, initials, num, rate, relativeTime } from '../src/lib/format.ts';

test('unknown numbers show as a dash, never 0', () => {
  assert.equal(num(null), '—');
  assert.equal(num(undefined), '—');
  assert.equal(num(0), '0');
  assert.equal(num(149), '149');
});

test('rates: two decimals, † when published by the source', () => {
  assert.equal(rate({ value: 131.86, reported: true }), '131.86†');
  assert.equal(rate({ value: 40, reported: false }), '40.00');
  assert.equal(rate({ value: null, reported: false }), '—');
  assert.equal(rate(null), '—');
});

test('relative time', () => {
  const now = new Date('2026-10-04T12:00:00Z');
  assert.equal(relativeTime(null, now), 'Never');
  assert.equal(relativeTime('2026-10-04T11:59:30Z', now), 'just now');
  assert.equal(relativeTime('2026-10-04T09:00:00Z', now), '3 hours ago');
  assert.equal(relativeTime('2026-10-03T12:00:00Z', now), '1 day ago');
  assert.equal(relativeTime('not a date', now), 'Unknown');
});

test('sorting puts unknown values last', () => {
  const rows = [
    { name: 'B', v: null },
    { name: 'A', v: 10 },
    { name: 'C', v: 50 },
    { name: 'D', v: 10 },
  ];
  const sorted = [...rows].sort(byDescNullsLast((r) => r.v, (r) => r.name)).map((r) => r.name);
  assert.deepEqual(sorted, ['C', 'A', 'D', 'B']);
});

test('initials', () => {
  assert.equal(initials('Sandeep Chandrasekharan Nair Roja'), 'SR');
  assert.equal(initials('Vishal'), 'V');
  assert.equal(initials('  '), '?');
});
