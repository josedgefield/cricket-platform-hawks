/* Hawks API client for the prototype pages.
   API base: ?api=https://host (remembered per browser), else http://localhost:8080. */
(function () {
  var DEFAULT_BASE = 'http://localhost:8080';

  function base() {
    var fromQuery = new URLSearchParams(location.search).get('api');
    if (fromQuery) {
      try { localStorage.setItem('hawks-api-base', fromQuery); } catch (e) {}
      return fromQuery.replace(/\/$/, '');
    }
    try { var saved = localStorage.getItem('hawks-api-base'); if (saved) return saved.replace(/\/$/, ''); } catch (e) {}
    return DEFAULT_BASE;
  }

  function get(path) {
    var ctrl = new AbortController();
    var timer = setTimeout(function () { ctrl.abort(); }, 10000);
    return fetch(base() + path, { signal: ctrl.signal, headers: { Accept: 'application/json' } })
      .then(function (res) {
        clearTimeout(timer);
        if (res.status === 204) return { data: null, meta: { sources: [] } };
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      }, function (err) { clearTimeout(timer); throw err; });
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

  /** Summarises meta.sources into "Synced 2 h ago · CSV export" with a stale flag. */
  function syncInfo(meta) {
    var sources = (meta && meta.sources) || [];
    var times = sources.map(function (s) { return s.syncedAt; }).filter(Boolean).sort();
    var oldest = times[0] || null;
    var stale = sources.some(function (s) { return s.stale; });
    var failed = sources.filter(function (s) { return s.lastSyncState === 'FAILED' || s.lastSyncState === 'SKIPPED_ROBOTS'; });
    var methods = Array.from(new Set(sources.map(function (s) { return s.method; }).filter(Boolean)));
    var label = { 'csv-export': 'CSV export', 'html-table': 'page table', 'manual-upload': 'uploaded CSV' };
    return {
      synced: oldest, stale: stale, failed: failed,
      text: (oldest ? 'Synced ' + ago(oldest) : 'Not synced yet') +
        (methods.length ? ' · ' + methods.map(function (m) { return label[m] || m; }).join(', ') : ''),
      url: sources[0] && sources[0].url
    };
  }

  window.HawksApi = { base: base, get: get, esc: esc, val: val, ago: ago, syncInfo: syncInfo };
})();
