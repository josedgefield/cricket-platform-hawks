// Run: node --test design/prototype/stats-data.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const S = require('./stats-data.js');

test('overs ↔ balls', () => {
  assert.equal(S.oversToBalls('3.4'), 22);
  assert.equal(S.oversToBalls('4'), 24);
  assert.equal(S.oversToBalls(''), null);
  assert.ok(Number.isNaN(S.oversToBalls('3.6')));
  assert.equal(S.ballsToOvers(22), '3.4');
  assert.equal(S.ballsToOvers(24), '4');
});

test('derived stats follow the null rules', () => {
  const d = S.derive({ bat: { runs: 50, inns: 2, no: 2, balls: 0 }, bowl: { runs: 20, balls: 0, wkts: 0 } });
  assert.equal(d.batAvg, null); // no dismissals → "—", not ∞
  assert.equal(d.sr, null);
  assert.equal(d.econ, null);
  assert.equal(d.bowlAvg, null);
});

test('combined totals sum sources; any unknown part makes the total unknown', () => {
  const arjun = S.table(S.ROWS, 'all').find((r) => r.player === 'Arjun M.');
  assert.equal(arjun.bat.runs, 512);
  assert.equal(arjun.d.batAvg.toFixed(1), '46.5');
  assert.equal(arjun.field.ct, null); // SCA lacks catches
  assert.deepEqual(arjun.sources.sort(), ['cricheroes', 'sca']);
  const rohit = S.table(S.ROWS, 'all').find((r) => r.player === 'Rohit I.');
  assert.equal(rohit.d.sr, null); // balls unknown
});

test('source filter keeps one source only', () => {
  const rows = S.table(S.ROWS, 'cricheroes');
  assert.ok(rows.every((r) => r.sources.length === 1 && r.sources[0] === 'cricheroes'));
  assert.equal(rows.find((r) => r.player === 'Arjun M.').bat.runs, 171);
});

test('CricHeroes batting CSV import: aliases, HS asterisk, warnings, duplicates', () => {
  const csv = [
    'Player,Mat,Inns,NO,Runs,HS,Avg,Balls,SR,4s,6s',
    '"Arjun Menon",5,5,1,200,88*,50.00,150,133.33,20,8',
    'Unknown Guy,2,2,0,10,7,5.00,12,83.33,1,0',
    'Daniel Tan,4,4,1,132,47,44.00,90,99.00,11,7',
    'arjun menon,1,1,0,1,1,1,1,100,0,0',
  ].join('\n');
  const res = S.importLeaderboard(csv, 'batting');
  assert.deepEqual(res.errors, []);
  const [a, u, d, dup] = res.rows;
  assert.equal(a.player, 'Arjun M.');
  assert.equal(a.values.hs, 88);
  assert.equal(a.warnings.length, 0);
  assert.equal(u.player, null); // unmatched → mapping queue, not auto-created
  assert.match(d.warnings[0], /Source SR 99/);
  assert.equal(dup.duplicate, true);

  const merged = S.applyImport(S.ROWS, res.rows, 'cricheroes');
  const arjun = merged.find((r) => r.player === 'Arjun M.' && r.source === 'cricheroes');
  assert.equal(arjun.bat.runs, 200);
  assert.equal(S.ROWS.find((r) => r.player === 'Arjun M.' && r.source === 'cricheroes').bat.runs, 171); // original untouched
  assert.ok(!merged.some((r) => r.player === null));
});

test('bowling CSV converts overs and flags bad overs', () => {
  const csv = 'Player\tMat\tOvers\tMaidens\tRuns\tWkts\tEcon\nKiran Patel\t4\t16\t1\t78\t9\t4.88\nFarhan Ali\t3\t10.7\t0\t61\t5\t5.5';
  const res = S.importLeaderboard(csv, 'bowling');
  assert.equal(res.rows[0].values.balls, 96);
  assert.equal(res.rows[0].warnings.length, 0);
  assert.equal(res.rows[1].values.balls, null);
  assert.match(res.rows[1].warnings[0], /not valid/);
});

test('missing player column is a clear error', () => {
  const res = S.importLeaderboard('Runs,Balls\n1,2', 'batting');
  assert.match(res.errors[0], /No “Player” column/);
});
