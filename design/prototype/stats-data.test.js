// Run: node --test design/prototype/stats-data.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const S = require('./stats-data.js');

function row(player, source, patch) {
  const r = S.emptyRow(player, source);
  for (const g of Object.keys(patch)) {
    if (typeof patch[g] === 'object' && patch[g] !== null) Object.assign(r[g], patch[g]);
    else r[g] = patch[g];
  }
  return r;
}

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

test('CricHeroes extract normalises without inventing counts', () => {
  const sandeep = S.ROWS.find((r) => r.player === 'Sandeep Chandrasekharan Nair Roja');
  assert.equal(sandeep.bat.runs, 149);
  assert.equal(sandeep.bat.inns, 4);
  assert.equal(sandeep.bat.balls, null); // not printed → unknown, not back-solved from SR
  assert.equal(sandeep.bat.no, null);
  assert.equal(sandeep.reported.sr, 131.86);
  assert.equal(sandeep.field.dis, 2);
  assert.equal(sandeep.field.st, null); // stumpings not printed
  assert.equal(sandeep.mat, 4);

  const shreyas = S.ROWS.find((r) => r.player === 'Puttur Shreyas');
  assert.equal(shreyas.bowl.wkts, 13);
  assert.equal(shreyas.bat.runs, null); // not in batting top 10 → unknown, not zero
  assert.equal(S.ROWS.length, 16); // 10 batting + 5 bowling-only + 1 fielding-only
  assert.ok(S.ROWS.every((r) => r.source === 'cricheroes'));
});

test('rates: computed when counts exist, else the source figure flagged as reported', () => {
  const t = S.table(S.ROWS, 'all');
  const hardik = t.find((r) => r.player === 'Hardik Shelat');
  assert.deepEqual(S.rate(hardik, 'sr'), { v: 193.94, reported: true });
  const computed = S.table([row('X', 'sca', { bat: { runs: 30, balls: 20 } })], 'all')[0];
  assert.deepEqual(S.rate(computed, 'sr'), { v: 150, reported: false });
});

test('combining sources: sums counts, unknown part → unknown total, drops per-source rates', () => {
  const rows = [
    row('A', 'sca', { mat: 3, bat: { inns: 3, no: 1, runs: 90, balls: 60 }, field: { ct: 2, st: 0 } }),
    row('A', 'cricheroes', { mat: 4, bat: { inns: 4, runs: 100 }, reported: { sr: 120 }, field: { ct: 1, dis: 1 } })
  ];
  const a = S.table(rows, 'all')[0];
  assert.equal(a.bat.runs, 190);
  assert.equal(a.bat.inns, 7);
  assert.equal(a.mat, 7);
  assert.equal(a.bat.balls, null);
  assert.equal(a.d.sr, null);
  assert.equal(a.reported.sr, null); // a single source's SR is not the combined SR
  assert.equal(S.rate(a, 'sr').v, null);
  assert.equal(a.field.ct, 3);
  assert.deepEqual(a.sources.sort(), ['cricheroes', 'sca']);
  assert.equal(S.table(rows, 'cricheroes')[0].bat.runs, 100);
});

test('leaders use real data, honour SR qualification and report ties', () => {
  const L = S.leaders(S.table(S.ROWS, 'all'));
  assert.equal(L.runs.value, 149);
  assert.equal(L.runs.rows[0].player, 'Sandeep Chandrasekharan Nair Roja');
  assert.equal(L.wkts.rows[0].player, 'Puttur Shreyas');
  assert.equal(L.sr.rows[0].player, 'Hardik Shelat'); // Cheeyanna Sunny (208, 2 inns) not qualified
  assert.equal(L.dis.value, 7);
  assert.deepEqual(L.dis.rows.map((r) => r.player).sort(), ['Nishaanth Sivakumar', 'Puttur Shreyas']);
});

test('points table keeps source strings and Hawks position', () => {
  const p = S.POINTS_TABLE.rows;
  assert.equal(p.length, 10);
  assert.equal(p[0].team, 'Hawks CC');
  assert.equal(p[0].pts, 27);
  assert.equal(p[0].nrr, '2.529');
  assert.equal(p.filter((r) => r.hawks).length, 1);
});

test('batting CSV import: aliases, HS asterisk, warnings, duplicates', () => {
  const csv = [
    'Player,Mat,Inn,NO,Runs,HS,Avg,Balls,SR,4s,6s',
    '"Hardik Shelat",5,4,1,90,40*,30.00,60,150.00,8,4',
    'Unknown Guy,2,2,0,10,7,5.00,12,83.33,1,0',
    'Vishal,4,4,0,56,20,14.00,77,99.00,4,0',
    'hardik shelat,1,1,0,1,1,1,1,100,0,0',
  ].join('\n');
  const res = S.importLeaderboard(csv, 'batting');
  assert.deepEqual(res.errors, []);
  const [h, u, v, dup] = res.rows;
  assert.equal(h.player, 'Hardik Shelat');
  assert.equal(h.values['bat.hs'], 40);
  assert.equal(h.warnings.length, 0);
  assert.equal(u.player, null); // unmatched → mapping queue, not auto-created
  assert.match(v.warnings[0], /Source SR 99/);
  assert.equal(dup.duplicate, true);

  const merged = S.applyImport(S.ROWS, res.rows, 'cricheroes');
  const hardik = merged.find((r) => r.player === 'Hardik Shelat');
  assert.equal(hardik.bat.runs, 90);
  assert.equal(hardik.bat.balls, 60);
  assert.equal(hardik.bowl.wkts, 9); // untouched tab kept
  assert.equal(S.ROWS.find((r) => r.player === 'Hardik Shelat').bat.runs, 64); // original untouched
  assert.ok(!merged.some((r) => r.player === 'Unknown Guy'));
});

test('bowling CSV in CricHeroes headers (Inn, W, Eco) converts overs and flags bad overs', () => {
  const csv = 'Player\tInn\tOvers\tMaidens\tRuns\tW\tEco\nPuttur Shreyas\t5\t21.1\t1\t77\t13\t3.64\nAlpin Mehta\t1\t4.7\t0\t18\t2\t4.5';
  const res = S.importLeaderboard(csv, 'bowling');
  assert.equal(res.rows[0].values['bowl.balls'], 127);
  assert.equal(res.rows[0].values['bowl.wkts'], 13);
  assert.equal(res.rows[0].warnings.length, 0);
  assert.equal(res.rows[1].values['bowl.balls'], undefined);
  assert.match(res.rows[1].warnings[0], /not valid/);
});

test('fielding CSV maps Dismissal and R/O', () => {
  const res = S.importLeaderboard('Player,Mat,Dismissal,Catches,R/O\nRahul Singh,2,3,2,1', 'fielding');
  assert.deepEqual(res.rows[0].values, { mat: 2, 'field.ct': 2, 'field.ro': 1, 'field.dis': 3 });
});

test('missing player column is a clear error', () => {
  const res = S.importLeaderboard('Runs,Balls\n1,2', 'batting');
  assert.match(res.errors[0], /No “Player” column/);
});

test('backend player stats map to canonical rows; only source rates stay as reported', () => {
  const r = S.fromApi({
    name: 'Alok Patra', matches: 9,
    batting: { inns: 9, notOuts: 0, runs: 360, balls: 316, highScore: 85, fours: 47, sixes: 8,
      average: { value: 40.0, reported: false }, strikeRate: { value: 113.92, reported: false } },
    bowling: null,
    fielding: { catches: 4, stumpings: 1, runOuts: 3, dismissals: 8 }
  }, 'sca');
  assert.equal(r.source, 'sca');
  assert.equal(r.bat.runs, 360);
  assert.equal(r.reported.sr, null); // calculated by the backend → recalculated from counts here
  assert.equal(r.bowl.wkts, null); // no bowling → unknown, not 0
  assert.equal(S.dismissals(S.table([r], 'all')[0]), 8);
  assert.equal(S.rate(S.table([r], 'all')[0], 'sr').v.toFixed(2), '113.92');

  const ch = S.fromApi({ name: 'Vishal', matches: null, batting: { inns: 4, notOuts: null, runs: 56, balls: null,
    highScore: null, fours: null, sixes: null, average: { value: 14.0, reported: true }, strikeRate: { value: 72.73, reported: true } } }, 'cricheroes');
  assert.deepEqual(S.rate(S.table([ch], 'all')[0], 'batAvg'), { v: 14, reported: true });
});
