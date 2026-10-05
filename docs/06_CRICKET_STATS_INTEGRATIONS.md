# 06 — Cricket Stats Integrations

## Sources
| Source | What | Known access | Status |
|---|---|---|---|
| **SCA Club League** (Singapore Cricket Association) | Fixtures, results, scorecards, player stats | Public **CricClubs** pages at `scores.cricketsingapore.com`, rendered as HTML on the server; **no JSON API** (confirmed from a browser HAR, 2026-10-05). The Excel/CSV/PDF buttons build the file in the browser from the HTML table | Hawks CC Div 3 2025 stats bundled as seed data. See *SCA (CricClubs)* below |
| **BPL** | Tournament fixtures/stats | Scored on **CricHeroes** (see below) | BPL 2025 = CricHeroes tournament `1500354` |
| **IAT30** | Tournament fixtures/stats | Unknown; check whether it's on CricHeroes too | ❓ open question |
| **CricHeroes** (platform) | Hawks team profile, match scorecards, team leaderboard, tournament results and points tables | Public web pages; **automated access is blocked by Cloudflare** (403, Oct 2026); no public API found | Manual CSV/paste import for v1. Ask CricHeroes about partner/data access |
| **Friendlies / internal** | Results, basic stats | Manual entry | In scope |

> SCA league data is hosted on **CricClubs**, a third-party league-management platform. Its terms of service decide what automated access is allowed. **Check them before building anything beyond CSV upload** (Q4).

## SCA (CricClubs)

**What a browser HAR of the Hawks team pages showed (captured 2026-10-05 13:52 SGT):**
- Every page is HTML rendered on the server (`*.do` endpoints). The HAR contains **no XHR/JSON data calls**, so there is no hidden API to use.
- The **Excel / CSV / PDF buttons are DataTables exports done in the browser.** The file is built from the HTML table already on the page. The only request they make is an audit ping (`downloadTeamPlayersDataAudit.do`), which returns no data. So "download CSV" and "copy the table" give exactly the same figures.
- Identifiers: Hawks CC is `teamId=2291`, `clubId=7683`, in league `296`, "SCA Club League 2025 – Division 3". Players have stable ids from `viewPlayer.do?playerId=…`, which the seed files keep as `SCA player ID`.

| Page | URL (under `https://scores.cricketsingapore.com/SingaporeCricketAssoc/`) | Table id | Feeds |
|---|---|---|---|
| Team batting | `teamBatting.do?teamId=2291&clubId=7683` | `tableBattingRecords` | batting totals |
| Team bowling | `teamBowling.do?teamId=2291&clubId=7683` | `tableBowlingRecords` | bowling totals |
| Team fielding | `teamFielding.do?teamId=2291&clubId=7683` | `tableFieldingRecords` | fielding totals |
| Player profile | `viewPlayer.do?playerId=…` | | `player_aliases` external id |
| Scorecard | `viewScorecard.do?matchId=…` (e.g. 7792, 7733, 7720) | | per-match counts (future) |
| Results / fixtures | the league results and fixtures pages, which link each `matchId` | | `matches` (future) |

**Current data:** `backend/src/main/resources/seed/sca/club-league-2025-div3/` holds `sca-div3-2025-batting.csv`, `sca-div3-2025-bowling.csv` and `sca-div3-2025-fielding.csv`, transcribed from the HAR exactly as printed (21 batters, 12 bowlers, 21 fielders). They load at startup into the competition "SCA Club League 2025 - Division 3", the same name `backend/scripts/import-sca.sh` uses, so a later manual import updates it instead of creating a duplicate. **Nothing fetches SCA pages automatically.**

**Updating:** after a match day, a stats admin downloads the three CSVs (or copies the tables) and runs `import-sca.sh` or posts them to the admin import endpoint. Unchanged files are no-ops. Scheduled fetching stays off until Q4 settles what CricClubs allows.

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
| Player / Name | `player_aliases.source_name` | Linked across sources by `player-links.csv` / the link endpoint (see *Combining sources*) |
| Mat, Inns, NO, Runs, Balls, 4s, 6s | batting counts | `HS` "88*" → 88 (not-out marker dropped for the season HS) |
| Overs, Maidens, Runs, Wkts | bowling counts | Overs → **balls**; a ball part > 5 is rejected with a warning |
| Catches, Stumpings, Run outs | fielding counts | |
| Avg, SR, Econ | *not stored* | Used only to cross-check our own calculation; a mismatch becomes a row warning |

**Combining with SCA:** see *Combining sources* below.

**What CricHeroes downloads contain (checked 2026-10-04).** The only download is a **PDF rendered as an image** (jsPDF, no text layer), so it can't be parsed and must be transcribed or OCR'd. Each PDF also lists only the **top 10** players:

| PDF | Printed columns | Not included |
|---|---|---|
| Batting leaderboard | Inn, Runs, Avg, SR | Mat, NO, balls, HS, 4s, 6s |
| Bowling leaderboard | Inn, W, Eco, Avg ("Dots" is printed empty) | overs/balls, maidens, runs conceded |
| Fielding leaderboard | Mat, Dismissal, Catches, R/O | stumpings |
| Points table | every group: M, W, L, D, T, NR, NRR, For, Against, Pts, Last 5 | — |

Consequences:
- **Counts that aren't printed stay `NULL`, unless they can be recovered exactly** (see *Exact count recovery* below). Rounded guesses are never stored.
- **Rates are shown as published,** marked "†", only when a single source contributes. They can't be combined with SCA rates, because combining needs the counts.
- **A player outside a top 10 is unknown for that category, not zero.**
- **Per-match scorecards are the better source.** They carry the full counts (balls, not-outs, HS, 4s/6s, overs, maidens, runs conceded, dismissal types including stumpings) for **every** player. Totals can then be recomputed and cross-checked against these leaderboards and the points table's team For/Against (Hawks: 912/123.3 for, 607/125 against).

**Current data:** `backend/src/main/resources/seed/cricheroes/bpl-2025/` holds `bpl-2025-batting.csv`, `bpl-2025-bowling.csv`, `bpl-2025-fielding.csv` and `bpl-2025-points-table-supreme.csv`, transcribed exactly as printed. They load at startup into the competition "BPL 2025". (The earlier static prototype, `design/prototype/stats-data.js`, holds `RAW_CRICHEROES`, the leaderboard values transcribed exactly as printed, and the BPL 2025 Supreme-group points table. `normaliseLeaderboard()` turns these into canonical rows, the same raw → normalised split as `source_records`. It predates the backend.)

**Prototype:** `design/prototype/stats.html` (club stats, source filter, points table, paste/CSV import preview), homepage season leaders, and the "My stats" tab in `app-matches.html` all read that file. Run the logic tests with `node --test design/prototype/stats-data.test.js`.

## Exact count recovery (amended 2026-10-05)

The BPL PDFs print rates but not the counts behind them. Without counts, BPL and SCA figures can't be added up. We therefore recover a missing count **only when exactly one whole number reproduces the published rate.** That rate is rounded half-up to the number of decimals printed.

| Missing count | Recovered from | Example (BPL 2025) |
|---|---|---|
| Batting balls | runs, SR: balls with `round(runs × 100 / balls) = SR` | Sandeep: 149 runs, SR 131.86 → 113 balls (only 113 fits) |
| Batting not-outs | inns, runs, Avg: dismissals with `round(runs / d) = Avg`, then NO = inns − d | Sandeep: 4 inns, Avg 49.67 → 3 dismissals → 1 NO |
| Bowling runs conceded | wickets, Avg | Shreyas: 13 wkts, Avg 5.92 → 77 runs |
| Bowling balls | runs conceded, Econ | Shreyas: 77 runs, Econ 3.64 → 127 balls |

Rules (in `CountRecovery`):
- If no candidate fits, or more than one does, the count **stays `NULL`**. This happens for Shashank, Avinash and Alpin's BPL bowling, and Anvay's BPL balls.
- Printed counts are never overwritten. Zero runs is never "recovered".
- Every recovered column is stored in `player_competition_stats.recovered_columns` and returned in the API's `recovered` list. The app marks those figures, and any rate calculated from them, with "‡".

This amends the earlier "never back-solve" rule. An exact, unique solution is arithmetic from published figures, not an estimate. Anything less certain is still left unknown.

## Combining sources

The "All sources" view adds up each player's **counts** across competitions and sources, then **recalculates** the rates from the totals. For example, Shashank: SCA 210 runs / 165 balls + BPL 96 / 81 → **306 runs / 246 balls, SR 124.39**.
- **Per category.** A source that doesn't list a player in a category (e.g. a BPL top-10 batting list without him) is left out of that category's total. It doesn't make it unknown. The API's `coverage` says which sources each category total includes, and the app shows "SCA only" when a total covers fewer sources than the player has.
- Within a category, if a contributing source lacks a count, the total is `NULL`. A partial sum would look complete.
- Highest score is the maximum. A published rate is kept (†) only when one source alone makes up the category.
- **Player identity across sources:** `seed/player-links.csv` (columns `Player, Source, Source name`) says which SCA and CricHeroes names are the same person, e.g. CricHeroes "Puttur Shreyas" = "Shreyas Puttur". A stats admin can add links with `POST /api/admin/stats/player-links` (JSON or that CSV). Linking moves the name's figures onto the player. It returns **409** if both already have figures for the same competition, because that would double count. Unlinked names appear as separate players, and are never merged by guessing.

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
