# Hawks platform backend

One Spring Boot application split into modules. It runs the same way on your laptop
and on the server: a container plus Postgres.

| Module | Package | What it owns |
|---|---|---|
| club | `sg.hawkscc.platform.club` | The club this deployment serves (`club_id` on all data) |
| stats | `sg.hawkscc.platform.stats` | Competitions, players, raw source records, per-source stats, standings, sync runs |
| security | `sg.hawkscc.platform.security` | Who can call what (default deny) |
| identity, finance, comms | *(next)* | Members and sign-in, the fee ledger, notices and availability |

Each module only uses other modules' top-level (public) classes. `ModularityTests`
fails the build if a module reaches into another's internals.

## Prerequisites

- **Java 21.** Gradle downloads it automatically if you don't have it.
- **Docker Desktop** (or Docker Engine), for Postgres locally and for the integration tests.
- An IDE. IntelliJ IDEA Community is free and is what these steps assume.

## Run it locally

```bash
cd backend
./gradlew bootRun          # Windows: gradlew.bat bootRun
```

This uses the `dev` profile. It:

1. starts Postgres and Mailpit from `compose.yaml` (first run downloads the images);
2. applies the database migrations in `src/main/resources/db/migration`;
3. loads the real CricHeroes BPL 2025 stats from `src/main/resources/seed/` (through the normal import);
4. serves the API on http://localhost:8080.

Then open:

| What | URL |
|---|---|
| API explorer (Swagger UI) | http://localhost:8080/swagger-ui.html |
| Player stats (JSON) | http://localhost:8080/api/stats/players |
| BPL 2025 competitions | http://localhost:8080/api/stats/competitions |
| Data source status | http://localhost:8080/api/stats/sources |
| Health | http://localhost:8080/actuator/health |
| Emails sent by the app | http://localhost:8025 (Mailpit) |

To try an **admin import** in Swagger UI, click **Authorize** and sign in as
`dev-admin` / `dev-admin-password`. This login only exists in the `dev` profile. Then
use `POST /api/admin/stats/competitions/{id}/imports/{kind}` with a CSV body, for example:

```
Player,Inn,Runs,Avg,SR
Hardik Shelat,4,90,30.00,150.00
```

## Debug it

In IntelliJ: **File → Open** the `backend` folder, wait for the Gradle import, then
open `HawksApplication` and click the bug icon next to `main`. Set **Active profiles**
to `dev` in the run configuration and the working directory to `backend` (so
`compose.yaml` is found). Breakpoints work anywhere: a good first one is
`StatsImportService.importLeaderboard`.

To look inside the database, connect any SQL client (IntelliJ's Database tab,
DBeaver, `psql`) to `localhost:5432`, database `hawks`, user `hawks`, password
`hawks-local-only`. Useful tables: `source_records` (exactly what was imported),
`player_competition_stats`, `standings`, `sync_runs` (every import attempt).

## Test it

```bash
./gradlew test       # unit + integration tests (needs Docker running)
./gradlew build      # tests + the runnable jar
```

- **Unit tests** cover the stats rules (null handling, overs as balls, rates) and the CSV parsers.
- **`StatsApiIntegrationTest`** starts the whole app against a real Postgres in a
  container (Testcontainers) and calls the HTTP API.
- **`ModularityTests`** checks the module boundaries.

The HTML test report is in `build/reports/tests/test/index.html`.

## Reset your local database

```bash
docker compose down -v     # deletes the local data volume
```

The next `bootRun` recreates the schema and reloads the seed.

## Rules this code follows (from `CLAUDE.md` and `docs/06`)

- **Missing is not zero.** If a source didn't publish a number it is stored as `NULL`
  and the API returns `null`. A combined total is `null` if any source lacks that value.
- **Raw is kept.** Every import stores the exact content in `source_records` with a
  SHA-256 hash; importing identical content again changes nothing.
- **Validate, then write in one transaction.** Invalid input is rejected with reasons
  (HTTP 422) and recorded as a failed sync run.
- **Rates are calculated** from counts. A source's own rate is returned only when the
  counts are missing, marked `"reported": true`.
