/* Hawks CC — stats data + pure helpers for the prototype.
   Mirrors the canonical model in docs/06: per-source rows, raw counts only,
   derived rates computed here with the null rules, overs stored as balls.
   Works in the browser (window.HawksStats) and in Node (require) for tests.

   ⚠ SAMPLE DATA. CricHeroes blocks automated access (Cloudflare 403, Oct 2026),
   so no real figures are loaded. Replace via the CSV import on stats.html or by
   editing ROWS below with figures copied from the sources. */
(function (root) {
  'use strict';

  var SOURCES = {
    sca: {
      id: 'sca',
      label: 'SCA',
      name: 'SCA Club League',
      competition: 'SCA Div 2 · 2026',
      method: 'CSV/Excel export upload',
      url: null,
      lastSynced: null,
      sample: true
    },
    cricheroes: {
      id: 'cricheroes',
      label: 'CricHeroes',
      name: 'CricHeroes',
      competition: 'BPL 2025',
      method: 'CSV copied from CricHeroes (automated access blocked)',
      teamId: '10178708',
      tournamentId: '1500354',
      url: 'https://cricheroes.com/team-profile/10178708/hawks-cc/leaderboard',
      links: {
        members: 'https://cricheroes.com/team-profile/10178708/hawks-cc/members',
        matches: 'https://cricheroes.com/team-profile/10178708/hawks-cc/matches',
        leaderboard: 'https://cricheroes.com/team-profile/10178708/hawks-cc/leaderboard',
        tournamentMatches: 'https://cricheroes.com/tournament/1500354/bpl-2025/matches/past-matches',
        pointsTable: 'https://cricheroes.com/tournament/1500354/bpl-2025/point-table'
      },
      lastSynced: null,
      sample: true
    }
  };

  // Source name → member display name. Unmatched names are flagged, never auto-created.
  var ALIASES = {
    'arjun menon': 'Arjun M.', 'arjun m': 'Arjun M.',
    'kiran patel': 'Kiran P.', 'kiran p': 'Kiran P.',
    'daniel tan': 'Daniel T.', 'daniel t': 'Daniel T.',
    'sam wilson': 'Sam W.', 'sam w': 'Sam W.',
    'rohit iyer': 'Rohit I.', 'rohit i': 'Rohit I.',
    'farhan ali': 'Farhan A.', 'farhan a': 'Farhan A.'
  };

  // One row per player per source. null = the source didn't record it.
  // bat: mat, inns, no, runs, balls, hs, fours, sixes · bowl: balls, runs, wkts, maidens · field: ct, st, ro
  var ROWS = [
    { player: 'Arjun M.', source: 'sca', bat: { mat: 8, inns: 8, no: 1, runs: 341, balls: 262, hs: 71, fours: 34, sixes: 9 }, bowl: { balls: 48, runs: 51, wkts: 3, maidens: 0 }, field: { ct: null, st: 0, ro: null } },
    { player: 'Arjun M.', source: 'cricheroes', bat: { mat: 4, inns: 4, no: 0, runs: 171, balls: 127, hs: 58, fours: 16, sixes: 6 }, bowl: { balls: 36, runs: 40, wkts: 6, maidens: 0 }, field: { ct: 2, st: 0, ro: 1 } },
    { player: 'Kiran P.', source: 'sca', bat: { mat: 8, inns: 5, no: 2, runs: 64, balls: 70, hs: 22, fours: 5, sixes: 1 }, bowl: { balls: 342, runs: 268, wkts: 18, maidens: 4 }, field: { ct: 3, st: 0, ro: 0 } },
    { player: 'Kiran P.', source: 'cricheroes', bat: { mat: 4, inns: 2, no: 1, runs: 19, balls: 15, hs: 14, fours: 2, sixes: 0 }, bowl: { balls: 96, runs: 78, wkts: 9, maidens: 1 }, field: { ct: 1, st: 0, ro: 0 } },
    { player: 'Daniel T.', source: 'sca', bat: { mat: 7, inns: 7, no: 1, runs: 236, balls: 158, hs: 64, fours: 21, sixes: 12 }, bowl: { balls: null, runs: null, wkts: null, maidens: null }, field: { ct: 4, st: 0, ro: null } },
    { player: 'Daniel T.', source: 'cricheroes', bat: { mat: 4, inns: 4, no: 1, runs: 132, balls: 90, hs: 47, fours: 11, sixes: 7 }, bowl: { balls: 12, runs: 19, wkts: 0, maidens: 0 }, field: { ct: 2, st: 0, ro: 0 } },
    { player: 'Sam W.', source: 'sca', bat: { mat: 8, inns: 6, no: 1, runs: 118, balls: 121, hs: 37, fours: 10, sixes: 2 }, bowl: { balls: 0, runs: 0, wkts: 0, maidens: 0 }, field: { ct: 9, st: 4, ro: 0 } },
    { player: 'Sam W.', source: 'cricheroes', bat: { mat: 4, inns: 3, no: 0, runs: 41, balls: 44, hs: 25, fours: 3, sixes: 1 }, bowl: { balls: 0, runs: 0, wkts: 0, maidens: 0 }, field: { ct: 4, st: 2, ro: 0 } },
    { player: 'Rohit I.', source: 'sca', bat: { mat: 6, inns: 5, no: 0, runs: 97, balls: null, hs: 31, fours: null, sixes: null }, bowl: { balls: 210, runs: 201, wkts: 9, maidens: 1 }, field: { ct: 2, st: 0, ro: 0 } },
    { player: 'Farhan A.', source: 'cricheroes', bat: { mat: 3, inns: 2, no: 1, runs: 12, balls: 10, hs: 9, fours: 1, sixes: 0 }, bowl: { balls: 66, runs: 61, wkts: 5, maidens: 0 }, field: { ct: 0, st: 0, ro: 0 } }
  ];

  var POINTS_TABLE = {
    source: 'cricheroes',
    competition: 'BPL 2025',
    sample: true,
    // nrr is kept as the source's string; we never recompute it.
    rows: [
      { team: 'Hawks CC', mat: 5, won: 4, lost: 1, nr: 0, pts: 8, nrr: '+1.214' },
      { team: 'Raffles Ravens', mat: 5, won: 3, lost: 2, nr: 0, pts: 6, nrr: '+0.402' },
      { team: 'Tanjong Tigers', mat: 5, won: 3, lost: 2, nr: 0, pts: 6, nrr: '+0.118' },
      { team: 'Kallang Kings', mat: 5, won: 2, lost: 2, nr: 1, pts: 5, nrr: null },
      { team: 'Marina Mariners', mat: 5, won: 0, lost: 4, nr: 1, pts: 1, nrr: '-1.733' }
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

  // Derived stats, per docs/06 null rules.
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

  // Combine rows. A sum is null if ANY contributing source lacks the value:
  // a partial total would look complete and be wrong.
  function sumField(rows, group, key) {
    var total = 0;
    for (var i = 0; i < rows.length; i++) {
      var v = rows[i][group] ? rows[i][group][key] : null;
      if (!isNum(v)) return null;
      total += v;
    }
    return rows.length ? total : null;
  }

  function maxField(rows, group, key) {
    var best = null;
    for (var i = 0; i < rows.length; i++) {
      var v = rows[i][group] ? rows[i][group][key] : null;
      if (!isNum(v)) return null;
      best = best === null ? v : Math.max(best, v);
    }
    return best;
  }

  var FIELDS = {
    bat: ['mat', 'inns', 'no', 'runs', 'balls', 'fours', 'sixes'],
    bowl: ['balls', 'runs', 'wkts', 'maidens'],
    field: ['ct', 'st', 'ro']
  };

  function combine(rows) {
    var out = { bat: {}, bowl: {}, field: {}, sources: [] };
    Object.keys(FIELDS).forEach(function (g) {
      FIELDS[g].forEach(function (k) { out[g][k] = sumField(rows, g, k); });
    });
    out.bat.hs = maxField(rows, 'bat', 'hs');
    rows.forEach(function (r) { if (out.sources.indexOf(r.source) < 0) out.sources.push(r.source); });
    return out;
  }

  // Players × chosen sources → [{ player, sources, bat, bowl, field, d }]
  function table(rows, sourceFilter) {
    var by = {};
    rows.forEach(function (r) {
      if (sourceFilter && sourceFilter !== 'all' && r.source !== sourceFilter) return;
      (by[r.player] = by[r.player] || []).push(r);
    });
    return Object.keys(by).map(function (p) {
      var c = combine(by[p]);
      c.player = p;
      c.d = derive(c);
      return c;
    });
  }

  function fmt(v, dp) {
    if (!isNum(v)) return '—';
    return dp ? v.toFixed(dp) : String(v);
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

  // Header aliases per leaderboard tab. Source rates (Avg, SR, Econ) are only used
  // to cross-check our own calculation, never stored.
  var HEADERS = {
    batting: {
      player: ['player', 'name', 'player name', 'batter'],
      mat: ['mat', 'm', 'matches'], inns: ['inns', 'innings', 'inn'], no: ['no', 'not out', 'not outs'],
      runs: ['runs', 'r'], balls: ['balls', 'b', 'bf', 'balls faced'], hs: ['hs', 'highest', 'highest score', 'best'],
      fours: ['4s', 'fours'], sixes: ['6s', 'sixes'],
      _avg: ['avg', 'average'], _sr: ['sr', 'strike rate']
    },
    bowling: {
      player: ['player', 'name', 'player name', 'bowler'],
      mat: ['mat', 'matches'], overs: ['overs', 'o', 'ov'], balls: ['balls', 'b'],
      maidens: ['maidens', 'mdns', 'md'], runs: ['runs', 'r', 'runs conceded'], wkts: ['wkts', 'w', 'wickets'],
      _econ: ['econ', 'economy', 'eco']
    },
    fielding: {
      player: ['player', 'name', 'player name', 'fielder'],
      mat: ['mat', 'm', 'matches'], ct: ['ct', 'catches', 'c'], st: ['st', 'stumpings'], ro: ['ro', 'run outs', 'run out', 'runouts']
    }
  };

  function intOrNull(raw, key, warn) {
    var s = String(raw === undefined ? '' : raw).trim();
    if (s === '' || s === '-' || s === '—') return null;
    if (key === 'hs') s = s.replace(/\*$/, '');
    if (!/^\d+$/.test(s)) { warn('“' + raw + '” is not a whole number for ' + key + ' (left blank)'); return null; }
    return Number(s);
  }

  function numOrNull(raw) {
    var s = String(raw === undefined ? '' : raw).trim();
    return s === '' || s === '-' || isNaN(Number(s)) ? null : Number(s);
  }

  function matchMember(name) {
    var k = String(name).toLowerCase().replace(/[^a-z ]/g, '').replace(/\s+/g, ' ').trim();
    return ALIASES[k] || null;
  }

  // Returns { rows: [{ sourceName, player|null, kind, values, warnings }], errors: [] }
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
        if (k === 'player' || k === 'overs' || k[0] === '_') return;
        v[k] = col[k] === undefined ? null : intOrNull(get(k), k, warn);
      });
      if (kind === 'bowling' && v.balls === null && col.overs !== undefined) {
        var b = oversToBalls(get('overs'));
        if (isNaN(b)) warn('Overs “' + get('overs') + '” is not valid (ball part must be 0–5)');
        else v.balls = b;
      }
      // Cross-check source rates against our own calculation.
      if (kind === 'batting') {
        var srcSr = numOrNull(get('_sr')), ourSr = ratio(v.runs, v.balls, 100);
        if (srcSr !== null && ourSr !== null && Math.abs(srcSr - ourSr) > 0.6) warn('Source SR ' + srcSr + ' ≠ calculated ' + ourSr.toFixed(1));
      }
      if (kind === 'bowling') {
        var srcEc = numOrNull(get('_econ')), ourEc = ratio(v.runs, v.balls, 6);
        if (srcEc !== null && ourEc !== null && Math.abs(srcEc - ourEc) > 0.06) warn('Source econ ' + srcEc + ' ≠ calculated ' + ourEc.toFixed(2));
      }
      if (!sourceName) errors.push('Row ' + (n + 2) + ': no player name');
      var key = sourceName.toLowerCase();
      if (seen[key]) warn('Duplicate of row ' + seen[key] + ' (later row ignored)');
      else seen[key] = n + 2;
      return { sourceName: sourceName, player: matchMember(sourceName), kind: kind, values: v, warnings: warnings, duplicate: seen[key] !== n + 2 };
    }).filter(function (r) { return r.sourceName; });
    return { rows: rows, errors: errors };
  }

  // Apply matched, non-duplicate import rows for one tab onto a source's rows.
  // Returns a new ROWS array; the original is untouched.
  function applyImport(rows, imported, source) {
    var groupFor = { batting: 'bat', bowling: 'bowl', fielding: 'field' };
    var out = rows.map(function (r) { return JSON.parse(JSON.stringify(r)); });
    imported.forEach(function (ir) {
      if (!ir.player || ir.duplicate) return;
      var g = groupFor[ir.kind];
      var target = out.filter(function (r) { return r.player === ir.player && r.source === source; })[0];
      if (!target) {
        target = { player: ir.player, source: source, bat: {}, bowl: {}, field: {} };
        FIELDS.bat.concat(['hs']).forEach(function (k) { target.bat[k] = null; });
        FIELDS.bowl.forEach(function (k) { target.bowl[k] = null; });
        FIELDS.field.forEach(function (k) { target.field[k] = null; });
        out.push(target);
      }
      Object.keys(ir.values).forEach(function (k) { if (k !== 'mat') target[g][k] = ir.values[k]; });
      if (ir.kind === 'batting' && ir.values.mat !== undefined) target.bat.mat = ir.values.mat;
    });
    return out;
  }

  var api = {
    SOURCES: SOURCES, ROWS: ROWS, POINTS_TABLE: POINTS_TABLE, ALIASES: ALIASES,
    oversToBalls: oversToBalls, ballsToOvers: ballsToOvers, derive: derive, combine: combine,
    table: table, fmt: fmt, parseCsv: parseCsv, importLeaderboard: importLeaderboard,
    applyImport: applyImport, matchMember: matchMember
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.HawksStats = api;
})(this);
