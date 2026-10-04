# 06 — Cricket Stats Integrations

## Sources
| Source | What | Known access | Status |
|---|---|---|---|
| **SCA Club League** (Singapore Cricket Association) | Fixtures, scorecards, player stats | Public web pages + downloadable CSV/Excel exports; **no documented public API found** (per CLAUDE.md research) | Confirm whether the club has a portal login and whether exports cover scorecards and stats |
| **BPL** | Tournament fixtures/stats | Unknown | ❓ open question |
| **IAT30** | Tournament fixtures/stats | Unknown | ❓ open question |
| **Friendlies / internal** | Results, basic stats | Manual entry | In scope |

> ASSUMPTION: SCA league data may be hosted on a third-party league-management platform. Its terms of service decide what automated access is allowed. **Check before building anything beyond CSV upload.**

## Design: adapter per source
```
             ┌──────────── SourceAdapter (interface) ────────────┐
             │ discover() → list of external items               │
             │ fetch(item) → RawRecord {source, kind, ext_id,    │
             │                           payload, sha256}        │
             │ normalise(raw) → CanonicalMatch/Stats (+warnings) │
             └───────────────────────────────────────────────────┘
   SCA-CSV adapter   BPL adapter   IAT30 adapter   Manual-entry adapter
         │                │              │                 │
         └─────► source_records (raw, hashed, immutable) ◄─┘
                         │ normalise
                         ▼
     matches / innings / player_match_stats  (+ external_ids, player_aliases)
```
The **access method** for each source is chosen in this order of preference:
1. **Documented or authorised API.**
2. **Export upload:** the stats admin downloads a CSV/Excel and uploads it to the admin console. This is the **v1 default for SCA.**
3. **Controlled web ingestion:** only with written permission or clear ToS allowance. It must be rate-limited, identify itself, use a cache, and be removable via a feature flag.

## Rules
- **Never invent data.** A missing value stays `NULL` and the UI shows "—". Strike rates and averages are computed only when the inputs exist.
- **Raw is kept.** Every normalised row links to its `source_records` row. Re-normalising from raw is always possible.
- **Dedupe:**
  - `content_sha256` on raw records (an unchanged file means a no-op);
  - `external_ids` unique per source;
  - matching when no id is available uses (date, teams, competition).
- **Player identity:** source names map to members through `player_aliases`. Unmatched names go to a **mapping queue** for the stats admin, and are never auto-created as members.
- **Corrections:** a stats admin override is stored as an override layer (`stat_overrides` with reason), not as an edit to imported values. Re-import keeps the override and flags any conflicts.
- **Provenance in UI:** every stats screen shows its source(s) and "Last synced <relative time>" from `sync_runs`.

## Safe failure checklist (applies to every adapter)
- [ ] Timeouts (10s per request) and retries with exponential backoff + jitter (max 3)
- [ ] Idempotent: re-running the same input changes nothing
- [ ] Dedupe by hash and external id
- [ ] Structured logs per `sync_run` (counts: new/updated/unchanged/warnings/errors)
- [ ] Alert to stats admin + operator on failure (email/Sentry)
- [ ] Manual "retry sync" and "re-normalise from raw" buttons
- [ ] Partial failure doesn't corrupt data: it's transactional per match
- [ ] "Last synced" + stale badge (> 7 days during season)

## Stats calculations (packages/domain/stats.ts)
| Stat | Formula | Null rule |
|---|---|---|
| Batting average | runs / dismissals | null if dismissals = 0 (show "—", not ∞) |
| Strike rate | runs / balls × 100 | null if balls null/0 |
| Economy | runs conceded / (balls/6) | null if balls null/0 |
| Bowling average | runs conceded / wickets | null if wickets = 0 |
| Bowling SR | balls / wickets | null if wickets = 0 |

Overs are stored as **balls** (e.g. 3.4 overs = 22 balls) to avoid decimal errors. The display converts back to overs.

## Phase-3 exit test
A full SCA season export imported into staging reproduces the source's own season totals for every Hawks player. Any differences must be listed and explained.
