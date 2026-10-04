/* Hawks API client for the prototype pages.
   Live mode:     the Hawks API (?api=https://host, remembered per browser; default http://localhost:8080 when the
                  page itself is served from localhost).
   Snapshot mode: data/sca-snapshot.js, i.e. real API responses exported by apps/api/scripts/export_snapshot.py.
                  Used when the page is not on localhost (e.g. a shared preview), with ?source=snapshot, or when
                  the live API can't be reached. The UI always says which one it is showing. */
(function () {
  var DEFAULT_BASE = 'http://localhost:8080';
  var params = new URLSearchParams(location.search);
  var onLocalhost = /^(localhost|127\.0\.0\.1)$/.test(location.hostname);
  var snapshot = window.HAWKS_SCA_SNAPSHOT || null;
  var state = { mode: null, liveError: null };

  function base() {
    var fromQuery = params.get('api');
    if (fromQuery) {
      try { localStorage.setItem('hawks-api-base', fromQuery); } catch (e) {}
      return fromQuery.replace(/\/$/, '');
    }
    try { var saved = localStorage.getItem('hawks-api-base'); if (saved) return saved.replace(/\/$/, ''); } catch (e) {}
    return DEFAULT_BASE;
  }

  function wantLive() {
    if (params.get('source') === 'snapshot') return false;
    if (params.get('api')) return true;
    return onLocalhost;
  }

  function fromSnapshot(path, why) {
    if (!snapshot || !snapshot.responses[path]) {
      throw new Error(why ? why + '; no snapshot for ' + path : 'No snapshot for ' + path);
    }
    state.mode = 'snapshot';
    return snapshot.responses[path];
  }

  function getLive(path) {
    var ctrl = new AbortController();
    var timer = setTimeout(function () { ctrl.abort(); }, 8000);
    return fetch(base() + path, { signal: ctrl.signal, headers: { Accept: 'application/json' } })
      .then(function (res) {
        clearTimeout(timer);
        if (res.status === 204) return { data: null, meta: { sources: [] } };
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      }, function (err) { clearTimeout(timer); throw err; });
  }

  function get(path) {
    if (!wantLive()) {
      return Promise.resolve().then(function () { return fromSnapshot(path); });
    }
    return getLive(path).then(function (r) { state.mode = 'live'; return r; }, function (err) {
      state.liveError = err;
      return fromSnapshot(path, 'Live API unreachable (' + (err.message || err) + ')');
    });
  }

  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  /** Unknown stays unknown: null/undefined renders as an em dash, never 0. */
  function val(v, digits) {
    if (v == null || v === '') return '—';
    if (typeof v === 'number' && digits != null) return v.toFixed(digits);
    return esc(v);
  }

  function ago(iso) {
    if (!iso) return 'never';
    var s = (Date.now() - new Date(iso).getTime()) / 1000;
    if (s < 90) return 'just now';
    if (s < 5400) return Math.round(s / 60) + ' min ago';
    if (s < 129600) return Math.round(s / 3600) + ' h ago';
    return Math.round(s / 86400) + ' days ago';
  }

  function day(iso) {
    return new Date(iso).toLocaleDateString('en-SG', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  /** Summarises meta.sources into a one-line provenance label, with stale/failed flags. */
  function syncInfo(meta) {
    var sources = (meta && meta.sources) || [];
    var times = sources.map(function (s) { return s.syncedAt; }).filter(Boolean).sort();
    var oldest = times[0] || null;
    var failed = sources.filter(function (s) { return s.lastSyncState === 'FAILED' || s.lastSyncState === 'SKIPPED_ROBOTS'; });
    var methods = Array.from(new Set(sources.map(function (s) { return s.method; }).filter(Boolean)));
    var label = { 'csv-export': 'CSV export', 'html-table': 'page table', 'manual-upload': 'SCA CSV export (uploaded)' };
    var how = methods.map(function (m) { return label[m] || m; }).join(', ');
    var text;
    if (state.mode === 'snapshot') {
      text = 'Snapshot of ' + (how || 'SCA data') + ' · exported ' + day(snapshot.generatedAt);
    } else {
      text = (oldest ? 'Synced ' + ago(oldest) : 'Not synced yet') + (how ? ' · ' + how : '');
    }
    return {
      synced: oldest, failed: state.mode === 'snapshot' ? [] : failed,
      stale: state.mode !== 'snapshot' && sources.some(function (s) { return s.stale; }),
      text: text, url: sources[0] && sources[0].url, mode: state.mode
    };
  }

  // ---------- shared cricket formatting ----------
  var OUR_TEAM = /hawks/i;
  function isUs(name) { return !!name && OUR_TEAM.test(name); }
  /** "CHAMPION CC - FRIENDS SQUAD" → "Champion CC - Friends Squad" (keeps CC/SCC/TNT/MUCC/PKR as written). */
  function teamName(name) {
    if (!name) return 'TBC';
    return name.split(/(\s+|-)/).map(function (w) {
      if (/^(CC|SCC|TNT|MUCC|PKR|XI|SG)$/.test(w)) return w;
      return w.length > 1 ? w.charAt(0) + w.slice(1).toLowerCase() : w;
    }).join('');
  }
  function opponent(m) { return isUs(m.teamOne) ? m.teamTwo : m.teamOne; }
  function crestText(name) {
    return (name || '?').replace(/[^A-Za-z0-9 ]/g, ' ').split(/\s+/).filter(Boolean)
      .filter(function (w) { return !/^(CC|SCC)$/i.test(w); }).map(function (w) { return w[0]; }).join('').slice(0, 2).toUpperCase() || '?';
  }
  function niceDate(iso, text, opts) {
    if (!iso) return esc(text || 'Date TBC');
    return new Date(iso + 'T00:00:00').toLocaleDateString('en-SG', opts || { weekday: 'short', day: 'numeric', month: 'short' });
  }
  /** Kick-off as a Date in Singapore time, or null when date/time are unknown. */
  function kickoff(f) {
    if (!f || !f.date) return null;
    var m = /(\d{1,2}):(\d{2})\s*(AM|PM)?/i.exec(f.time || '');
    if (!m) return null;
    var h = +m[1] % 12 + (m[3] && m[3].toUpperCase() === 'PM' ? 12 : 0);
    if (!m[3]) h = +m[1];
    return new Date(f.date + 'T' + String(h).padStart(2, '0') + ':' + m[2] + ':00+08:00');
  }
  var OUTCOME = { WON: ['status-ok', 'Won'], LOST: ['status-bad', 'Lost'], TIED: ['status-info', 'Tied'], NO_RESULT: ['status-info', 'No result'], ABANDONED: ['status-info', 'Abandoned'] };
  function outcomeChip(o) {
    var c = OUTCOME[o];
    return c ? '<span class="status ' + c[0] + '">' + c[1] + '</span>' : '';
  }

  window.HawksApi = {
    base: base, get: get, esc: esc, val: val, ago: ago, syncInfo: syncInfo, state: state, snapshot: snapshot,
    isUs: isUs, teamName: teamName, opponent: opponent, crestText: crestText, niceDate: niceDate, kickoff: kickoff,
    outcomeChip: outcomeChip
  };
})();
