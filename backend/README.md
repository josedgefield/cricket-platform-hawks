# Hawks platform backend

One Spring Boot application split into modules. It runs the same way on your laptop
and on the server: a container plus Postgres.

| Module | Package | What it owns |
|---|---|---|
| club | `sg.hawkscc.platform.club` | The club this deployment serves (`club_id` on all data) |
| stats | `sg.hawkscc.platform.stats` | Competitions, players, raw source records, per-source stats, standings, sync runs |
| identity | `sg.hawkscc.platform.identity` | Members, invites, passwords, sessions, roles (player/admin/superuser) |
| audit | `sg.hawkscc.platform.audit` | Append-only `audit_log` of sensitive changes |
| security | `sg.hawkscc.platform.security` | Who can call what (default deny, bearer session tokens) |
| finance, comms | *(next)* | The fee ledger, notices and availability |

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
3. loads the bundled stats from `src/main/resources/seed/` through the normal import: the player links, CricHeroes BPL 2025 and SCA Club League 2025 Division 3 (see `seed/README.md`);
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

**Signing in locally.** The `dev` profile creates a superuser on first run:
`dev-admin@hawks.local` / `dev-admin-password` (it only exists in `dev`; a server never has it).
Sign in from the app, or in Swagger UI: run `POST /api/auth/sign-in` with that email and
password, copy the `token` from the answer, click **Authorize** and paste it. Invite emails
land in Mailpit at http://localhost:8025, where you can click the link.

To try an **admin import** in Swagger UI once authorised, use
`POST /api/admin/stats/competitions/{id}/imports/{kind}` with a CSV body, for example:

```
Player,Inn,Runs,Avg,SR
Hardik Shelat,4,90,30.00,150.00
```

### Import SCA stats with curl

Copy each leaderboard table (batting, bowling, fielding) from the SCA website and paste
it, unchanged, into `sca-batting.csv`, `sca-bowling.csv` and `sca-fielding.csv` in one
folder. The parser understands SCA's copied layout (sort arrows in the header, each
player over three lines). Then, with the app running:

```bash
scripts/import-sca.sh /path/to/folder "SCA Club League"
```

Each response lists new players and any warnings (for example a published rate that
disagrees with the counts). Running it again with the same files changes nothing.

The stats page in `design/prototype/stats.html` reads `/api/stats/**` from
`http://localhost:8080` (add `?api=http://host:port` to the page URL to change it).
The dev profile allows it through CORS when served from `localhost:8000`.

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

The next `bootRun` recreates the schema and reloads the seed. Do this once after pulling the
SCA seed and player links (October 2026). Otherwise an SCA competition you imported by hand earlier, and
BPL rows stored before count recovery, stay as they were, because unchanged files aren't re-imported.

## Members and sign-in

Nobody signs themselves up. Roles: **player**, **admin** and **superuser** (support), see `docs/03`.

- **Locally:** sign in as the dev superuser above, open *More → Manage users* in the app and invite
  someone. The email appears in Mailpit (http://localhost:8025); its link opens the app's
  *accept invite* screen, where they choose a password.
- **On a server:** set `HAWKS_BOOTSTRAP_SUPERUSER_EMAIL` (and the SMTP settings) in `deploy/.env`.
  On first start, if no superuser exists, that person gets an invite. They invite everyone else.
- **API:** `/api/auth/*` (sign-in, sign-out, invitations, password reset), `/api/me`,
  `/api/admin/members/*`. Send the token from sign-in as `Authorization: Bearer <token>`.
- Sessions end after 60 days without use for players, 14 days for admins and superusers, and at
  once on sign-out, password reset, role change or deactivation.
- "Deleting" a member deactivates them: they can't sign in, and their history stays.

## Linking a player's names across sources

The same person can appear as "Shreyas Puttur" on SCA and "Puttur Shreyas" on CricHeroes. Add the pair to
`src/main/resources/seed/player-links.csv`, or post it as a stats admin:

```bash
token=$(curl -s -H 'Content-Type: application/json' \
  -d '{"email":"dev-admin@hawks.local","password":"dev-admin-password"}' \
  http://localhost:8080/api/auth/sign-in | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  -d '{"player":"Shreyas Puttur","source":"cricheroes","sourceName":"Puttur Shreyas"}' \
  http://localhost:8080/api/admin/stats/player-links
```

That name's figures move onto the player, and the "All sources" totals add them up. The answer is 409 if
both already have figures for the same competition.

## Rules this code follows (from `CLAUDE.md` and `docs/06`)

- **Missing is not zero.** If a source didn't publish a number it is stored as `NULL`
  and the API returns `null`. A combined total is `null` if any source lacks that value.
- **Raw is kept.** Every import stores the exact content in `source_records` with a
  SHA-256 hash; importing identical content again changes nothing.
- **Validate, then write in one transaction.** Invalid input is rejected with reasons
  (HTTP 422) and recorded as a failed sync run.
- **Rates are calculated** from counts. A source's own rate is returned only when the
  counts are missing, marked `"reported": true`.
