# 06 — Cricket Stats Integrations

## Sources
| Source | What | Known access | Status |
|---|---|---|---|
| **SCA Club League** (Singapore Cricket Association) | Fixtures, scorecards, player stats | Public web pages + downloadable CSV/Excel exports; **no documented public API found** (per CLAUDE.md research) | Confirm whether the club has a portal login and whether exports cover scorecards and stats |
| **BPL** | Tournament fixtures/stats | Scored on **CricHeroes** (see below) | BPL 2025 = CricHeroes tournament `1500354` |
| **IAT30** | Tournament fixtures/stats | Unknown; check whether it's on CricHeroes too | ❓ open question |
| **CricHeroes** (platform) | Hawks team profile, match scorecards, team leaderboard, tournament results and points tables | Public web pages; **automated access is blocked by Cloudflare** (403, Oct 2026); no public API found | Manual CSV/paste import for v1. Ask CricHeroes about partner/data access |
| **Friendlies / internal** | Results, basic stats | Manual entry | In scope |

> ASSUMPTION: SCA league data may be hosted on a third-party league-management platform. Its terms of service decide what automated access is allowed. **Check before building anything beyond CSV upload.**

## CricHeroes (BPL and other tournaments)

**Known pages** (Hawks CC team `10178708`, BPL 2025 tournament `1500354`):

| Page | URL | Feeds |
|---|---|---|
| Team members | https://cricheroes.com/team-profile/10178708/hawks-cc/members | `player_aliases` (CricHeroes name → member) |
| Team matches | https://cricheroes.com/team-profile/10178708/hawks-cc/matches | `matches`, `innings`, scorecards |
| Team leaderboard | https://cricheroes.com/team-profile/10178708/hawks-cc/leaderboard | season batting/bowling/fielding totals (cross-check) |
| BPL 2025 past matches | https://cricheroes.com/tournament/1500354/bpl-2025/matches/past-matches | tournament fixtures/results |
| BPL 2025 points table | https://cricheroes.com/tournament/1500354/bpl-2025/point-table | `competition_standings` (NRR stored as the source string, never recomputed) |

**Access findings (2026-10-04):** every page above returns **HTTP 403 "Sorry, you have been blocked" from Cloudflare** to server-side requests (curl and a fetch tool). That is the site saying no to automated access, so we **do not** work around it with headless browsers, rotating user agents or similar techniques. That would breach principle 5 and is fragile anyway. No public, documented CricHeroes API was found.

**Access plan, in order of preference:**
1. **Ask CricHeroes** (or the BPL organiser, who has an organiser account) for an authorised export or API. Record the answer in `docs/15` (Q19).
2. **v1 default: manual import.** The stats admin opens the leaderboard (or a scorecard) in a normal browser, copies the table or downloads it, and pastes or uploads it in the admin console. This runs through the same stage → preview → commit pipeline as the SCA CSV (`docs/11`).
3. Controlled web ingestion **only** with written permission from CricHeroes.

**Adapter:** `CricHeroesCsvAdapter`, source id `cricheroes`, `external_ids` keyed on CricHeroes team/tournament/match/player ids where present.

| CricHeroes column (leaderboard) | Canonical field | Notes |
|---|---|---|
| Player / Name | `player_aliases.source_name` | Unmatched names go to the mapping queue and are never auto-created |
| Mat, Inns, NO, Runs, Balls, 4s, 6s | batting counts | `HS` "88*" → 88 (not-out marker dropped for the season HS) |
| Overs, Maidens, Runs, Wkts | bowling counts | Overs → **balls**; a ball part > 5 is rejected with a warning |
| Catches, Stumpings, Run outs | fielding counts | |
| Avg, SR, Econ | *not stored* | Used only to cross-check our own calculation; a mismatch becomes a row warning |

**Combining with SCA:** stats are stored per source and combined at read time. A combined total is `NULL` if **any** contributing source lacks that value, because a partial sum looks complete and would be wrong. Every stats view shows which sources contributed (source chips) and lets the viewer filter to one source.

**What CricHeroes downloads contain (checked 2026-10-04).** The only download is a **PDF rendered as an image** (jsPDF, no text layer), so it can't be parsed and must be transcribed or OCR'd. Each PDF also lists only the **top 10** players:

| PDF | Printed columns | Not included |
|---|---|---|
| Batting leaderboard | Inn, Runs, Avg, SR | Mat, NO, balls, HS, 4s, 6s |
| Bowling leaderboard | Inn, W, Eco, Avg ("Dots" is printed empty) | overs/balls, maidens, runs conceded |
| Fielding leaderboard | Mat, Dismissal, Catches, R/O | stumpings |
| Points table | every group: M, W, L, D, T, NR, NRR, For, Against, Pts, Last 5 | — |

Consequences:
- **Counts that aren't printed stay `NULL`.** We do **not** back-solve balls from SR, or runs conceded from Avg × W. The values would be rounded guesses presented as facts.
- **Rates are shown as published,** marked "†", and only for a single source. They can't be combined with SCA rates, because combining needs the counts.
- **A player outside a top 10 is unknown for that category, not zero.**
- **Per-match scorecards are the better source.** They carry the full counts (balls, not-outs, HS, 4s/6s, overs, maidens, runs conceded, dismissal types including stumpings) for **every** player. Totals can then be recomputed and cross-checked against these leaderboards and the points table's team For/Against (Hawks: 912/123.3 for, 607/125 against).

**Current data:** `design/prototype/stats-data.js` holds `RAW_CRICHEROES`, the leaderboard values transcribed exactly as printed, and the BPL 2025 Supreme-group points table. `normaliseLeaderboard()` turns these into canonical rows, the same raw → normalised split as `source_records`. SCA is not imported, so its filter shows an empty state.

**Prototype:** `design/prototype/stats.html` (club stats, source filter, points table, paste/CSV import preview), homepage season leaders, and the "My stats" tab in `app-matches.html` all read that file. Run the logic tests with `node --test design/prototype/stats-data.test.js`.

## Design: adapter per source
```
             ┌──────────── SourceAdapter (interface) ────────────┐
             │ discover() → list of external items               │
             │ fetch(item) → RawRecord {source, kind, ext_id,    │
             │                           payload, sha256}        │
             │ normalise(raw) → CanonicalMatch/Stats (+warnings) │
             └───────────────────────────────────────────────────┘
   SCA-CSV adapter  CricHeroes adapter  IAT30 adapter  Manual-entry adapter
                    (BPL; CSV/paste v1)
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
