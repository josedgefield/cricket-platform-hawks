# 09 — Tech Architecture

> **Decision (2026-10-04):** the backend is a **Spring Boot modular monolith (Java 21)** on
> PostgreSQL, self-hosted in Docker on a near-free VM. This replaces the earlier
> Supabase-centred plan. Reasons: the club wants a backend it can **run, test and debug
> locally** and host for **nearly nothing**; business rules (especially the finance
> ledger) live in ordinary Java code that can be stepped through and unit-tested, rather
> than in database policies and Edge Functions.

## Stack decision
| Layer | Choice | Why |
|---|---|---|
| Backend | **Spring Boot 4.1**, Java 21, Gradle (wrapper in repo) | Mature, well-documented; one language for all server rules |
| Structure | **Modular monolith**, boundaries enforced by **Spring Modulith** tests | Separation without running many services; finance needs single-DB transactions |
| Database | **PostgreSQL 16** | Relational, suits a ledger |
| Migrations | **Flyway** (`backend/src/main/resources/db/migration`) | Versioned SQL, applied on startup and in CI |
| Data access | Spring `JdbcClient` with explicit SQL | Readable, easy to debug; no ORM magic around money |
| API | REST + JSON, documented with OpenAPI (springdoc, Swagger UI in dev) | Mobile and web clients share one API |
| Auth | **Spring Security** with the app's own `identity` module: invite-only, email + password (bcrypt), opaque bearer session tokens stored as hashes and revocable at once; TOTP 2FA for admins and superusers before finance goes live (decided 2026-10-09) | No separate identity server to host; members aren't emailed a code at every login |
| Mobile + player web | **Expo** (iOS, Android, web) calling the API | One UI codebase |
| Admin web | Expo web build first; a dedicated admin front end only if dense tables demand it | Fewer codebases to maintain |
| Email | SMTP to a free-tier provider (e.g. Brevo/Resend); **Mailpit** locally | |
| Errors/logs | Structured JSON logs (ECS), Sentry free tier (PII scrubbed), `sync_runs` + audit tables | |
| CI | GitHub Actions: Gradle build + Testcontainers + Modulith check + Docker build | |
| Hosting | **Oracle Cloud Always Free** Arm VM, Singapore: Docker Compose (app + Postgres + Caddy) | ~S$0/month; see `deploy/README.md` |
| Fallback hosting | Google Cloud Run + Neon (same container) | If the free VM is ever unavailable |

**Rejected or deferred:**
- **Microservices:** many JVMs, inter-service auth, and distributed transactions around the ledger, for under 50 users. Revisit only if a module has a real reason to scale or deploy separately; the ingestion adapters are the likeliest candidate.
- **Supabase as the backend:** rules live in RLS policies and Edge Functions, which are harder to debug locally; the Pro plan alone is most of the budget. Supabase or Neon remain options as *managed Postgres* only.
- **Keycloak / external identity server:** another heavy service to run and secure.
- **Kubernetes, message brokers:** no need at this scale. Modulith's in-process events cover module-to-module notifications.

## Modules (`backend/src/main/java/sg/hawkscc/platform/…`)
| Module | Owns | Status |
|---|---|---|
| `club` | Club (tenant) context; `club_id` on all data, one club deployed | ✅ |
| `stats` | Competitions, players + aliases, raw `source_records`, per-source stats, standings, `sync_runs`, CSV/paste imports | ✅ first slice |
| `security` | HTTP security rules (default deny), bearer token filter | ✅ |
| `identity` | Members, invites, passwords, sessions, roles (player/admin/superuser), password reset | ✅ first slice (no 2FA yet) |
| `audit` | Append-only `audit_log` | ✅ |
| `finance` | Fee schedules, charges, payment claims, payments, allocations, adjustments (append-only ledger) | after identity |
| `comms` | Announcements, reactions/replies, availability, notification outbox | later |
| `imports` | Excel member/finance import (stage → preview → commit → rollback) | with finance |

**Rules:**
- A module may use another module's **top-level package** only (its public API). Subpackages (`domain`, `persistence`, `web`, …) are internal.
- Cross-module side effects go through **application events** (Spring Modulith), e.g. `PaymentConfirmed` → `comms` sends a receipt.
- `ModularityTests` fails the build on violations or cycles.

## Repository layout
```
backend/            Spring Boot app (Gradle), Dockerfile, compose.yaml for local services
deploy/             production compose, Caddyfile, backup script, VM runbook
apps/mobile/        Expo app (to come)
design-system/      design tokens and guidance
design/prototype/   static HTML prototype
docs/               specs
.github/workflows/  backend.yml (build, test, image)
```

## Environments
| Env | How | Data |
|---|---|---|
| local | `./gradlew bootRun` (dev profile starts Postgres + Mailpit via Docker Compose) | seed data (public CricHeroes stats; synthetic members later) |
| CI | Testcontainers Postgres per test run | test fixtures |
| prod | Oracle VM, `deploy/compose.prod.yaml`, `prod` profile | real |

A staging environment can be a second Compose project on the same VM when finance work starts.

## Where logic lives
| Logic | Where |
|---|---|
| Ledger mutations (confirm claim, allocate, adjust) | `finance` services, one DB transaction each; append-only enforced by DB triggers too |
| Balances/statements | SQL views + service layer (never a stored balance) |
| Authorisation | Spring Security + method-level checks per role; tests per role × endpoint |
| Excel import | `imports` module: parse (Apache POI), validate, stage, preview, commit |
| Cricket data ingestion | `stats` module adapters (CSV/paste now; API only with authorised access) |
| Notifications | `comms` outbox table processed by a scheduled job with retries |
| Payment webhooks (phase 5) | `finance` webhook endpoint: signature check, idempotent by event id |

## Security model
- **The application is the security boundary.** Every endpoint is denied unless a rule allows it; admin endpoints require roles; finance/admin require MFA (`aal2`-equivalent session flag).
- **Database role for the app** can read/write data but not change schema (migrations run with a separate role in production).
- **Append-only ledger** enforced twice: no update/delete code paths, and DB triggers that reject `UPDATE`/`DELETE` on ledger tables.
- Postgres row-level security is **optional defence in depth** later; it is not required for correctness.

## CI pipeline (`.github/workflows/backend.yml`)
1. Gradle build: compile, unit tests, integration tests against Testcontainers Postgres, Spring Modulith boundary check.
2. Docker image build (proves the Dockerfile).
3. Next: publish the image to GHCR on `main`, deploy to the VM over SSH, CodeQL, dependency review.

## Operations
- **Backups:** nightly `pg_dump` → gzip → `age` encryption → Oracle Object Storage (private, 90-day lifecycle). Private key kept off the server. **Quarterly restore drill**, logged here.
- **Monitoring:** container health checks; free uptime monitor on `/actuator/health`; Sentry alerts; daily digest of failed `sync_runs`.
- **Releases:** merge to `main` → CI → image → deploy; Flyway migrates on start. Roll back by redeploying the previous image tag (migrations are written to be backwards compatible for one release).
- **Runbooks:** restore DB, rotate secrets, revoke a session, reprocess a failed import, PDPA breach response, move to the fallback host.

## Multi-tenancy (future-proof, not SaaS)
- `club_id` on every tenant table; the deployment serves one club (`hawks.club-slug`).
- Club config lives in `clubs.settings`, not in code.
- No club sign-up flow.
