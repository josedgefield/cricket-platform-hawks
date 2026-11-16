// Run: npm test  (Node 22.18+ runs the TypeScript module directly by stripping types)
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  byDescNullsLast,
  coverageNote,
  initials,
  isIsoDate,
  money,
  num,
  parseCents,
  rate,
  recoveredMark,
  relativeTime,
  shortDate,
} from '../src/lib/format.ts';

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

test('recovered figures get ‡, unknown ones stay a dash', () => {
  assert.equal(recoveredMark('113', true), '113‡');
  assert.equal(recoveredMark('113', false), '113');
  assert.equal(recoveredMark('—', true), '—');
});

test('coverage note only when a total leaves a source out', () => {
  const label = (s) => (s === 'sca' ? 'SCA' : 'BPL');
  assert.equal(coverageNote(['sca', 'cricheroes'], ['sca'], label), 'SCA only');
  assert.equal(coverageNote(['sca', 'cricheroes'], ['sca', 'cricheroes'], label), null);
  assert.equal(coverageNote(['sca'], ['sca'], label), null);
  assert.equal(coverageNote(['sca', 'cricheroes'], undefined, label), null);
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

test('money shows dollars and cents, never rounded', () => {
  assert.equal(money(123450), 'S$1,234.50');
  assert.equal(money(5), 'S$0.05');
  assert.equal(money(0), 'S$0.00');
  assert.equal(money(-2000), '-S$20.00');
  assert.equal(money(100000000), 'S$1,000,000.00');
  assert.equal(money(null), '—');
});

test('typed amounts become whole cents without floating-point drift', () => {
  assert.equal(parseCents('25'), 2500);
  assert.equal(parseCents('25.5'), 2550);
  assert.equal(parseCents(' S$1,250.00 '), 125000);
  assert.equal(parseCents('0.29'), 29);
  assert.equal(parseCents('19.99'), 1999);
  assert.equal(parseCents('0'), null);
  assert.equal(parseCents('-5'), null);
  assert.equal(parseCents('12.345'), null);
  assert.equal(parseCents('abc'), null);
  assert.equal(parseCents(''), null);
});

test('dates', () => {
  assert.equal(shortDate('2026-11-06'), '6 Nov 2026');
  assert.equal(shortDate('2026-11-16T23:30:00Z'), '16 Nov 2026');
  assert.equal(shortDate(null), '—');
  assert.equal(isIsoDate('2026-02-28'), true);
  assert.equal(isIsoDate('2026-02-30'), false);
  assert.equal(isIsoDate('28/02/2026'), false);
});
