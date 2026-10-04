# Hawks API — SCA stats service (Spring Boot)

Pulls Hawks team data from the Singapore Cricket Association site (`scores.cricketsingapore.com`, team 2291 / club 7683) through each page's **CSV export**. It normalises the data and serves it to the app's Stats page through `ScaStatsController`.

| Page | Dataset | Used for |
|---|---|---|
| `viewTeam.do` | `players` | Squad list |
| `teamResults.do` | `results` | Match scores, season record and form |
| `teamSchedule.do` | `schedule` | Upcoming matches |
| `teamBatting.do` | `batting` | Batting table and leaders |
| `teamBowling.do` | `bowling` | Bowling table and leaders |
| `teamFielding.do` | `fielding` | Fielding table and leaders |

## Run
Requires Java 21. Gradle comes via the wrapper.
```bash
cd apps/api
SCA_ADMIN_KEY=change-me ./gradlew bootRun
# Swagger UI:  http://localhost:8080/swagger-ui.html
# Stats page:  cd ../../design/prototype && python3 -m http.server 8000 → http://localhost:8000/app-stats.html
./gradlew test       # 35 tests
```

| Env var | Default | Purpose |
|---|---|---|
| `SCA_ADMIN_KEY` | *(unset)* | Enables `POST /api/sca/sync` and `/import/*`. These are disabled when unset |
| `SCA_SYNC_ENABLED` | `true` | Scheduled sync on or off |
| `SCA_SYNC_ON_STARTUP` | `true` | Run one sync when the app starts |
| `SCA_STORAGE_DIR` | `./data/sca` | Snapshot plus raw CSV archive (git-ignored) |
| `SCA_CORS_ORIGINS` | `http://localhost:8000,…` | Front-end origins allowed to GET |

Other settings (schedule, timeouts, date order, leader qualifications) live in `src/main/resources/application.yml` under `sca.*`.

## API
Every response is `{ "data": …, "meta": { "sources": [{ dataset, url, method, syncedAt, stale, lastSyncState, lastSyncMessage }] } }`.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/sca/players` | Squad |
| GET | `/api/sca/players/{idOrName}` | Combined profile: batting, bowling and fielding |
| GET | `/api/sca/batting?sort=runs&limit=` | Sorts: runs, average, strikerate, innings, fours, sixes, name |
| GET | `/api/sca/bowling?sort=wickets` | Sorts: wickets, economy, average, strikerate, overs, maidens, name |
| GET | `/api/sca/fielding?sort=catches` | Sorts: catches, dismissals, stumpings, runouts, name |
| GET | `/api/sca/leaders?top=3` | Most runs, best average (min 3 inns), best SR (min 60 balls), most wickets, best economy (min 10 overs), most catches, most dismissals |
| GET | `/api/sca/results?competition=&opponent=&limit=` | Newest first |
| GET | `/api/sca/record` | Won, lost, tied, NR and abandoned counts, plus last-five form |
| GET | `/api/sca/schedule?includePast=false&limit=` | Upcoming fixtures by Singapore date |
| GET | `/api/sca/schedule/next` | Next fixture, or 204 |
| GET | `/api/sca/raw/{dataset}` | The table exactly as exported |
| GET | `/api/sca/sync/status` | Last sync per dataset |
| POST | `/api/sca/sync` | Admin (`X-Admin-Key`). 202, runs in the background |
| POST | `/api/sca/import/{dataset}` | Admin. Multipart `file`: a CSV downloaded by hand from the page's CSV button |

## How extraction works
1. Read `robots.txt` and respect it. If the site can't be reached, every dataset is marked FAILED and the last good data stays.
2. Load the team page with the configured User-Agent: one request at a time, at least 3s apart, 20s timeout, and 3 attempts with exponential backoff and jitter on network errors, 429 or 5xx.
3. **If the CSV button has a real download URL**, download the CSV (`method = csv-export`).
   - The response must actually be CSV, not a login or error page.
   - Player and match ids are borrowed from the page's own links when the rows line up.
4. **If the CSV button builds the file in the browser** (for example a DataTables "CSV" button), there is no URL to download. The service then reads the same table the button exports (`method = html-table`). Set `sca.allow-html-table-fallback=false` to accept only true downloads.
5. The raw table is archived as `data/sca/raw/<dataset>/<time>-<sha>.csv`, and identical content is recorded as UNCHANGED.
6. Normalisation maps the export's headers through alias lists, so a renamed column doesn't break the parser.
   - Blank, `-`, `DNB` and similar values become `null`, never 0.
   - Overs are converted to balls.
   - Unmapped columns are kept in each record's `extra`.

## Load the club's CSV exports (works without site access)
```bash
# with the API running and SCA_ADMIN_KEY=change-me
F=src/test/resources/fixtures/sca-2025-div3
for d in players results schedule; do
  curl -H "X-Admin-Key: change-me" -F "file=@$F/$d.csv" http://localhost:8080/api/sca/import/$d
done
# refresh the prototype's offline snapshot from the API
python3 scripts/export_snapshot.py
```
`design/prototype/data/sca-snapshot.js` holds real API responses. The prototype uses it whenever it isn't served from localhost, or with `?source=snapshot`, and labels the data as a snapshot.

## Verified against real exports (SCA Clubs Division 3 - 2025, HAWKS CC)
The club supplied the players, results and schedule CSVs (`src/test/resources/fixtures/sca-2025-div3/`). `RealScaExportTest` pins the parser to them:
- [x] Dates are `MM/dd/yyyy` (`sca.date-order: MDY`).
- [x] The team appears as `HAWKS CC` in result text, so W/L is derived correctly (7 W, 4 L, 1 abandoned).
- [x] The results export has no per-team score columns. Both innings come in `SCORE SUMMARY` (`HAWKS CC: 282/4(28.0)WARRIORS CC 2: 152/10(22.2)`) and are split per team.
- [x] Squad player ids come from the `CC Player Id` column.
- [x] Schedule columns map directly (`Date`, `Time`, `Team One`, `Team Two`, `Ground`). Umpires and scorers are kept in `extra`.
- [ ] Batting, bowling and fielding exports haven't been supplied yet, so their header aliases are still untested on real files.
- [ ] Automatic download: whether each page's CSV button has a downloadable URL or builds the file client-side is still unknown, because the build environment can't reach the site.

## Storage
File-based for now (`snapshot.json` plus the raw CSV archive). This gets replaced by the planned Postgres `source_records` / `sync_runs` tables (docs/04) when the database lands.
