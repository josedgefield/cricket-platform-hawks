/* Hawks CC — stats data + pure helpers for the prototype.
   Mirrors docs/06: raw source extracts are kept as captured, then normalised
   into per-source canonical rows; derived rates are computed with the null
   rules; overs are stored as balls. Missing values stay null — never inferred.
   Works in the browser (window.HawksStats) and in Node (require) for tests. */
(function (root) {
  'use strict';

  var SOURCES = {
    sca: {
      id: 'sca',
      label: 'SCA',
      name: 'SCA Club League',
      competition: 'SCA Club League',
      method: 'CSV/Excel export upload',
      status: 'none',
      lastSynced: null
    },
    cricheroes: {
      id: 'cricheroes',
      label: 'CricHeroes',
      name: 'CricHeroes',
      competition: 'BPL 2025 · Supreme group',
      method: 'Leaderboard + points table PDFs, transcribed by hand (automated access is blocked)',
      teamId: '10178708',
      tournamentId: '1500354',
      links: {
        members: 'https://cricheroes.com/team-profile/10178708/hawks-cc/members',
        matches: 'https://cricheroes.com/team-profile/10178708/hawks-cc/matches',
        leaderboard: 'https://cricheroes.com/team-profile/10178708/hawks-cc/leaderboard',
        tournamentMatches: 'https://cricheroes.com/tournament/1500354/bpl-2025/matches/past-matches',
        pointsTable: 'https://cricheroes.com/tournament/1500354/bpl-2025/point-table'
      },
      status: 'partial',
      lastSynced: '2026-10-04',
      limits: 'Leaderboards list the top 10 per tab and give rates without the underlying balls, not-outs or runs conceded.'
    }
  };

  /* ---------- Raw extracts (as captured; do not edit to "fix" values) ----------
     Source: CricHeroes PDF downloads of the Hawks CC team leaderboard
     (hawks-cc-{batting,bowling,fielding}-leaderboard.pdf) and BPL 2025 points
     table (points_table_BPL_2025.pdf), supplied by the club on 2026-10-04.
     The PDFs are images, so values were transcribed visually. Columns are
     exactly those printed; anything not printed is not here. */
  var RAW_CRICHEROES = {
    capturedOn: '2026-10-04',
    // [player, Inn, Runs, Avg, SR]
    batting: [
      ['Sandeep Chandrasekharan Nair Roja', 4, 149, '49.67', '131.86'],
      ['Nishaanth Sivakumar', 4, 109, '36.33', '136.25'],
      ['Shashank Patwal', 4, 81, '20.25', '112.50'],
      ['Sanyam Makkar', 1, 70, '70.00', '148.94'],
      ['Hardik Shelat', 3, 64, '64.00', '193.94'],
      ['Vishal', 4, 56, '14.00', '72.73'],
      ['Cheeyanna Sunny', 2, 52, '52.00', '208.00'],
      ['Girish Balaraman', 3, 43, '14.33', '51.81'],
      ['Aditya Chandrasekhar', 3, 39, '13.00', '108.33'],
      ['Puttur Thejas', 4, 37, '18.50', '112.12']
    ],
    // [player, Inn, W, Eco, Avg]  ("Dots" is printed but empty)
    bowling: [
      ['Puttur Shreyas', 5, 13, '3.64', '5.92'],
      ['Hardik Shelat', 3, 9, '5.55', '6.78'],
      ['Shashank Patwal', 5, 7, '4.75', '12'],
      ['Puttur Thejas', 4, 4, '4.67', '17.5'],
      ['Anvay Kokate', 3, 4, '6.62', '13.25'],
      ['Avinash Kumar Singh', 4, 3, '8.00', '32'],
      ['Alpin Mehta', 1, 2, '4.50', '9'],
      ['Cheeyanna Sunny', 2, 2, '6.33', '28.5'],
      ['Sachin Padghan', 1, 1, '5.67', '17'],
      ['Nishaanth Sivakumar', 2, 1, '6.25', '25']
    ],
    // [player, Mat, Dismissal, Catches, R/O]  (stumpings are not printed)
    fielding: [
      ['Nishaanth Sivakumar', 5, 7, 0, 0],
      ['Puttur Shreyas', 7, 7, 7, 0],
      ['Anvay Kokate', 7, 4, 3, 1],
      ['Shashank Patwal', 7, 4, 4, 0],
      ['Sandeep Chandrasekharan Nair Roja', 4, 2, 1, 0],
      ['Vishal', 4, 2, 2, 0],
      ['Girish Balaraman', 5, 2, 1, 0],
      ['Puttur Thejas', 7, 2, 2, 0],
      ['Rahul Singh', 1, 1, 1, 0],
      ['Cheeyanna Sunny', 2, 1, 1, 0]
    ]
  };

  var POINTS_TABLE = {
    source: 'cricheroes',
    competition: 'BPL 2025',
    group: 'Supreme (league matches)',
    capturedOn: '2026-10-04',
    // Order, NRR, For/Against and Last 5 are kept exactly as CricHeroes prints them.
    rows: [
      { team: 'Hawks CC', mat: 8, won: 5, lost: 0, drawn: 0, tied: 0, nr: 3, nrr: '2.529', for: '912/123.3', against: '607/125', pts: 27, last5: 'W-W-W-W-W', hawks: true },
      { team: 'HP', mat: 9, won: 5, lost: 2, drawn: 0, tied: 0, nr: 2, nrr: '1.085', for: '1227/171.4', against: '1061/175', pts: 24, last5: 'L-W-W-W-L' },
      { team: 'Chargers Cricket Club', mat: 8, won: 6, lost: 2, drawn: 0, tied: 0, nr: 0, nrr: '0.668', for: '1269/177.4', against: '1227/189.3', pts: 24, last5: 'L-W-W-L-W' },
      { team: 'Elite Mavericks', mat: 8, won: 5, lost: 3, drawn: 0, tied: 0, nr: 0, nrr: '0.146', for: '1395/193', against: '1381/195', pts: 21, last5: 'L-W-W-W-W' },
      { team: 'Glorious Cricket Club', mat: 7, won: 4, lost: 3, drawn: 0, tied: 0, nr: 0, nrr: '-0.506', for: '934/146.1', against: '1024/148.3', pts: 16, last5: 'W-W-W-L-W' },
      { team: 'Legends X1', mat: 8, won: 3, lost: 4, drawn: 0, tied: 0, nr: 1, nrr: '-0.033', for: '1114/175', against: '1059/165.3', pts: 15, last5: 'L-W-L-W-W' },
      { team: 'Cracking Willows', mat: 8, won: 3, lost: 5, drawn: 0, tied: 0, nr: 0, nrr: '-1.091', for: '1024/197.1', against: '1061/168.5', pts: 12, last5: 'L-W-L-L-L' },
      { team: 'Misfits Cricket', mat: 8, won: 2, lost: 5, drawn: 0, tied: 0, nr: 1, nrr: '-1.215', for: '956/145.1', against: '1122/143.5', pts: 10, last5: 'W-L-L-W-L' },
      { team: 'Grab Cricket Club', mat: 8, won: 2, lost: 6, drawn: 0, tied: 0, nr: 0, nrr: '-0.661', for: '1069/194.4', against: '1213/197.1', pts: 8, last5: 'L-W-L-L-L' },
      { team: 'Uttarakhand Cricket Club', mat: 8, won: 1, lost: 6, drawn: 0, tied: 0, nr: 1, nrr: '-1.278', for: '832/160.4', against: '977/151.2', pts: 6, last5: 'L-L-L-L-L' }
    ]
  };

  /* ---------- numbers ---------- */

  function isNum(v) { return typeof v === 'number' && isFinite(v); }

  // "3.4" → 22 balls. Returns null for blank, NaN on malformed input (ball part > 5).
  function oversToBalls(o) {
    if (o === null || o === undefined || String(o).trim() === '' || String(o).trim() === '-') return null;
    var m = /^(\d+)(?:\.(\d))?$/.exec(String(o).trim());
    if (!m) return NaN;
    var b = m[2] ? Number(m[2]) : 0;
    if (b > 5) return NaN;
    return Number(m[1]) * 6 + b;
  }

  function ballsToOvers(b) {
    if (!isNum(b)) return null;
    return Math.floor(b / 6) + (b % 6 ? '.' + (b % 6) : '');
  }

  function ratio(a, b, mult) {
    if (!isNum(a) || !isNum(b) || b === 0) return null;
    return (a / b) * (mult || 1);
  }

  function numOrNull(raw) {
    var s = String(raw === undefined || raw === null ? '' : raw).trim();
    return s === '' || s === '-' || s === '—' || isNaN(Number(s)) ? null : Number(s);
  }

  /* ---------- canonical rows ---------- */

  var FIELDS = {
    bat: ['inns', 'no', 'runs', 'balls', 'fours', 'sixes'],
    bowl: ['inns', 'balls', 'runs', 'wkts', 'maidens'],
    field: ['ct', 'st', 'ro', 'dis']
  };
  var RATES = ['batAvg', 'sr', 'econ', 'bowlAvg'];

  function emptyRow(player, source) {
    var r = { player: player, source: source, mat: null, bat: { hs: null }, bowl: {}, field: {}, reported: {} };
    Object.keys(FIELDS).forEach(function (g) { FIELDS[g].forEach(function (k) { r[g][k] = null; }); });
    RATES.forEach(function (k) { r.reported[k] = null; });
    return r;
  }

  // Raw leaderboard extract → canonical rows. Source rates go to `reported`
  // untouched; counts the source didn't print stay null.
  function normaliseLeaderboard(raw, source) {
    var by = {}, order = [];
    function row(name) {
      if (!by[name]) { by[name] = emptyRow(name, source); order.push(name); }
      return by[name];
    }
    raw.batting.forEach(function (x) {
      var r = row(x[0]);
      r.bat.inns = x[1]; r.bat.runs = x[2];
      r.reported.batAvg = numOrNull(x[3]); r.reported.sr = numOrNull(x[4]);
    });
    raw.bowling.forEach(function (x) {
      var r = row(x[0]);
      r.bowl.inns = x[1]; r.bowl.wkts = x[2];
      r.reported.econ = numOrNull(x[3]); r.reported.bowlAvg = numOrNull(x[4]);
    });
    raw.fielding.forEach(function (x) {
      var r = row(x[0]);
      r.mat = x[1]; r.field.dis = x[2]; r.field.ct = x[3]; r.field.ro = x[4];
    });
    return order.map(function (n) { return by[n]; });
  }

  var ROWS = normaliseLeaderboard(RAW_CRICHEROES, 'cricheroes');

  // Source name → display name. Seeded from names seen in source data; anything
  // else from an import goes to the mapping queue and is never auto-created.
  function aliasKey(name) { return String(name).toLowerCase().replace(/[^a-z ]/g, '').replace(/\s+/g, ' ').trim(); }
  var ALIASES = {};
  ROWS.forEach(function (r) { ALIASES[aliasKey(r.player)] = r.player; });

  function matchMember(name) { return ALIASES[aliasKey(name)] || null; }

  /* ---------- derived + combined ---------- */

  // Computed from counts, per docs/06 null rules.
  function derive(t) {
    var dismissals = isNum(t.bat.inns) && isNum(t.bat.no) ? t.bat.inns - t.bat.no : null;
    return {
      batAvg: ratio(t.bat.runs, dismissals),
      sr: ratio(t.bat.runs, t.bat.balls, 100),
      econ: ratio(t.bowl.runs, t.bowl.balls, 6),
      bowlAvg: ratio(t.bowl.runs, t.bowl.wkts),
      bowlSr: ratio(t.bowl.balls, t.bowl.wkts),
      dismissals: dismissals
    };
  }

  // A sum is null if ANY contributing source lacks the value:
  // a partial total would look complete and be wrong.
  function sumOf(rows, get) {
    if (!rows.length) return null;
    var total = 0;
    for (var i = 0; i < rows.length; i++) {
      var v = get(rows[i]);
      if (!isNum(v)) return null;
      total += v;
    }
    return total;
  }

  function combine(rows) {
    var out = { bat: {}, bowl: {}, field: {}, reported: {}, sources: [] };
    out.mat = sumOf(rows, function (r) { return r.mat; });
    Object.keys(FIELDS).forEach(function (g) {
      FIELDS[g].forEach(function (k) { out[g][k] = sumOf(rows, function (r) { return r[g][k]; }); });
    });
    var hs = sumOf(rows, function (r) { return r.bat.hs; }) === null ? null : Math.max.apply(null, rows.map(function (r) { return r.bat.hs; }));
    out.bat.hs = hs;
    // A source's own rate is only meaningful for that source alone.
    RATES.forEach(function (k) { out.reported[k] = rows.length === 1 ? rows[0].reported[k] : null; });
    rows.forEach(function (r) { if (out.sources.indexOf(r.source) < 0) out.sources.push(r.source); });
    return out;
  }

  // Players × chosen sources → [{ player, sources, mat, bat, bowl, field, reported, d }]
  function table(rows, sourceFilter) {
    var by = {}, order = [];
    rows.forEach(function (r) {
      if (sourceFilter && sourceFilter !== 'all' && r.source !== sourceFilter) return;
      if (!by[r.player]) { by[r.player] = []; order.push(r.player); }
      by[r.player].push(r);
    });
    return order.map(function (p) {
      var c = combine(by[p]);
      c.player = p;
      c.d = derive(c);
      return c;
    });
  }

  // Our calculation when the counts exist, otherwise the source's own figure.
  function rate(r, key) {
    if (isNum(r.d[key])) return { v: r.d[key], reported: false };
    if (isNum(r.reported[key])) return { v: r.reported[key], reported: true };
    return { v: null, reported: false };
  }

  function dismissals(r) {
    if (isNum(r.field.dis)) return r.field.dis;
    return isNum(r.field.ct) && isNum(r.field.st) ? r.field.ct + r.field.st : null;
  }

  function fmt(v, dp) {
    if (!isNum(v)) return '—';
    return dp ? v.toFixed(dp) : String(v);
  }

  // Leaders for the website. Ties are returned together; nulls never lead.
  function leaders(t, opts) {
    var minInns = (opts && opts.minSrInns) || 3;
    function top(list, get) {
      var best = null, who = [];
      list.forEach(function (r) {
        var v = get(r);
        if (!isNum(v)) return;
        if (best === null || v > best) { best = v; who = [r]; } else if (v === best) who.push(r);
      });
      return best === null ? null : { value: best, rows: who };
    }
    return {
      runs: top(t, function (r) { return r.bat.runs; }),
      wkts: top(t, function (r) { return r.bowl.wkts; }),
      sr: top(t.filter(function (r) { return isNum(r.bat.inns) && r.bat.inns >= minInns; }), function (r) { return rate(r, 'sr').v; }),
      dis: top(t, dismissals),
      minSrInns: minInns
    };
  }

  /* ---------- CSV import (CricHeroes leaderboard copied/exported as CSV) ---------- */

  function parseCsv(text) {
    var rows = [], row = [], cell = '', q = false;
    text = String(text).replace(/^﻿/, '');
    for (var i = 0; i < text.length; i++) {
      var ch = text[i];
      if (q) {
        if (ch === '"' && text[i + 1] === '"') { cell += '"'; i++; }
        else if (ch === '"') q = false;
        else cell += ch;
      } else if (ch === '"') q = true;
      else if (ch === ',' || ch === '\t') { row.push(cell); cell = ''; }
      else if (ch === '\n' || ch === '\r') {
        if (ch === '\r' && text[i + 1] === '\n') i++;
        row.push(cell); cell = '';
        if (row.some(function (c) { return c.trim() !== ''; })) rows.push(row);
        row = [];
      } else cell += ch;
    }
    row.push(cell);
    if (row.some(function (c) { return c.trim() !== ''; })) rows.push(row);
    return rows;
  }

  // Header aliases per leaderboard tab → [group, field]. Rates go to `reported`.
  var HEADERS = {
    batting: {
      player: ['player', 'name', 'player name', 'batter'],
      mat: ['mat', 'm', 'matches'],
      'bat.inns': ['inns', 'innings', 'inn'], 'bat.no': ['no', 'not out', 'not outs'],
      'bat.runs': ['runs', 'r'], 'bat.balls': ['balls', 'b', 'bf', 'balls faced'], 'bat.hs': ['hs', 'highest', 'highest score', 'best'],
      'bat.fours': ['4s', 'fours'], 'bat.sixes': ['6s', 'sixes'],
      'reported.batAvg': ['avg', 'average'], 'reported.sr': ['sr', 'strike rate']
    },
    bowling: {
      player: ['player', 'name', 'player name', 'bowler'],
      mat: ['mat', 'matches'],
      'bowl.inns': ['inns', 'innings', 'inn'], overs: ['overs', 'o', 'ov'], 'bowl.balls': ['balls', 'b'],
      'bowl.maidens': ['maidens', 'mdns', 'md'], 'bowl.runs': ['runs', 'r', 'runs conceded'], 'bowl.wkts': ['wkts', 'w', 'wickets'],
      'reported.econ': ['econ', 'economy', 'eco'], 'reported.bowlAvg': ['avg', 'average']
    },
    fielding: {
      player: ['player', 'name', 'player name', 'fielder'],
      mat: ['mat', 'm', 'matches'],
      'field.ct': ['ct', 'catches', 'c'], 'field.st': ['st', 'stumpings'],
      'field.ro': ['ro', 'r/o', 'run outs', 'run out', 'runouts'], 'field.dis': ['dismissal', 'dismissals', 'dis']
    }
  };

  function intOrNull(raw, key, warn) {
    var s = String(raw === undefined ? '' : raw).trim();
    if (s === '' || s === '-' || s === '—') return null;
    if (key === 'bat.hs') s = s.replace(/\*$/, '');
    if (!/^\d+$/.test(s)) { warn('“' + raw + '” is not a whole number for ' + key.split('.').pop() + ' (left blank)'); return null; }
    return Number(s);
  }

  // Returns { rows: [{ sourceName, player|null, kind, values: {'bat.runs': 12, ...}, warnings, duplicate }], errors }
  function importLeaderboard(text, kind) {
    var spec = HEADERS[kind];
    if (!spec) return { rows: [], errors: ['Unknown tab “' + kind + '”'] };
    var grid = parseCsv(text);
    if (grid.length < 2) return { rows: [], errors: ['Need a header row and at least one player row.'] };
    var head = grid[0].map(function (h) { return h.trim().toLowerCase(); });
    var col = {};
    Object.keys(spec).forEach(function (k) {
      for (var i = 0; i < head.length; i++) if (spec[k].indexOf(head[i]) >= 0 && col[k] === undefined) col[k] = i;
    });
    if (col.player === undefined) return { rows: [], errors: ['No “Player” column found. Headers seen: ' + grid[0].join(', ')] };

    var seen = {}, errors = [];
    var rows = grid.slice(1).map(function (cells, n) {
      var warnings = [];
      function warn(m) { warnings.push(m); }
      function get(k) { return col[k] === undefined ? undefined : cells[col[k]]; }
      var sourceName = String(get('player') || '').trim();
      var v = {};
      Object.keys(spec).forEach(function (k) {
        if (k === 'player' || k === 'overs' || col[k] === undefined) return;
        v[k] = k.indexOf('reported.') === 0 ? numOrNull(get(k)) : intOrNull(get(k), k, warn);
      });
      if (kind === 'bowling' && v['bowl.balls'] == null && col.overs !== undefined) {
        var b = oversToBalls(get('overs'));
        if (isNaN(b)) warn('Overs “' + get('overs') + '” is not valid (ball part must be 0–5)');
        else v['bowl.balls'] = b;
      }
      // Cross-check source rates against our own calculation where counts allow.
      if (kind === 'batting') {
        var ourSr = ratio(v['bat.runs'], v['bat.balls'], 100);
        if (v['reported.sr'] != null && ourSr !== null && Math.abs(v['reported.sr'] - ourSr) > 0.6) warn('Source SR ' + v['reported.sr'] + ' ≠ calculated ' + ourSr.toFixed(1));
      }
      if (kind === 'bowling') {
        var ourEc = ratio(v['bowl.runs'], v['bowl.balls'], 6);
        if (v['reported.econ'] != null && ourEc !== null && Math.abs(v['reported.econ'] - ourEc) > 0.06) warn('Source econ ' + v['reported.econ'] + ' ≠ calculated ' + ourEc.toFixed(2));
      }
      if (!sourceName) errors.push('Row ' + (n + 2) + ': no player name');
      var key = sourceName.toLowerCase();
      var duplicate = !!seen[key];
      if (duplicate) warn('Duplicate of row ' + seen[key] + ' (this row is ignored)');
      else seen[key] = n + 2;
      return { sourceName: sourceName, player: matchMember(sourceName), kind: kind, values: v, warnings: warnings, duplicate: duplicate };
    }).filter(function (r) { return r.sourceName; });
    return { rows: rows, errors: errors };
  }

  // Apply matched, non-duplicate import rows onto a source's rows.
  // Only columns present in the import are written. Returns a new array.
  function applyImport(rows, imported, source) {
    var out = rows.map(function (r) { return JSON.parse(JSON.stringify(r)); });
    imported.forEach(function (ir) {
      if (!ir.player || ir.duplicate) return;
      var target = out.filter(function (r) { return r.player === ir.player && r.source === source; })[0];
      if (!target) { target = emptyRow(ir.player, source); out.push(target); }
      Object.keys(ir.values).forEach(function (k) {
        var p = k.split('.');
        if (p.length === 1) target[p[0]] = ir.values[k];
        else target[p[0]][p[1]] = ir.values[k];
      });
    });
    return out;
  }

  /* ---------- backend API (GET /api/stats/players?source=…) ---------- */

  // A rate the backend took from the source (it couldn't calculate it) goes to
  // `reported`; a calculated one is recalculated here from the same counts.
  function reportedOnly(rate) {
    return rate && rate.reported && rate.value !== null ? Number(rate.value) : null;
  }

  // One backend PlayerStats (already one source) → one canonical row.
  function fromApi(p, source) {
    var r = emptyRow(p.name, source), b = p.batting, w = p.bowling, f = p.fielding;
    r.mat = p.matches;
    if (b) {
      r.bat = { inns: b.inns, no: b.notOuts, runs: b.runs, balls: b.balls, hs: b.highScore, fours: b.fours, sixes: b.sixes };
      r.reported.batAvg = reportedOnly(b.average);
      r.reported.sr = reportedOnly(b.strikeRate);
    }
    if (w) {
      r.bowl = { inns: w.inns, balls: w.balls, runs: w.runs, wkts: w.wickets, maidens: w.maidens };
      r.reported.econ = reportedOnly(w.economy);
      r.reported.bowlAvg = reportedOnly(w.average);
    }
    if (f) r.field = { ct: f.catches, st: f.stumpings, ro: f.runOuts, dis: f.dismissals };
    return r;
  }

  var api = {
    SOURCES: SOURCES, RAW_CRICHEROES: RAW_CRICHEROES, ROWS: ROWS, POINTS_TABLE: POINTS_TABLE, ALIASES: ALIASES,
    fromApi: fromApi,
    oversToBalls: oversToBalls, ballsToOvers: ballsToOvers, derive: derive, combine: combine, table: table,
    rate: rate, dismissals: dismissals, leaders: leaders, fmt: fmt, emptyRow: emptyRow,
    normaliseLeaderboard: normaliseLeaderboard, parseCsv: parseCsv, importLeaderboard: importLeaderboard,
    applyImport: applyImport, matchMember: matchMember
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.HawksStats = api;
})(this);
