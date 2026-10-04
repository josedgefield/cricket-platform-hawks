# 09 — Tech Architecture

## Stack decision
| Layer | Choice | Why |
|---|---|---|
| Language | TypeScript (strict) everywhere; SQL for DB logic | One language, shared types |
| Monorepo | pnpm workspaces + Turborepo | Fast, cached builds; standard |
| Mobile + player web | **Expo** (managed) + Expo Router + NativeWind | One codebase for iOS, Android and web; OTA updates via EAS Update |
| Admin web | **Next.js** (App Router) + Tailwind + TanStack Table | Dense desktop tables, file uploads, server actions |
| Backend | **Supabase**: Postgres 15+, Auth, Storage, Edge Functions (Deno), Realtime (optional) | Managed, RLS, SG region, low ops |
| Validation | zod schemas in `packages/domain` shared by client, server and functions | |
| Data fetching | TanStack Query (mobile + admin) | Caching, offline-friendly |
| Charts | Victory Native (mobile), Recharts (admin/web) | Per skill chart guidance |
| Push | Expo Notifications | APNs/FCM abstraction |
| Email | Resend + React Email templates | |
| Errors | Sentry (Expo + Next.js + Edge) with PII scrubbing | |
| CI/CD | GitHub Actions; EAS Build/Submit; Vercel for admin | |

**Club decision (2026-10-04):** the SCA stats ingestion and query API is a **Spring Boot (Java 21)** service in `apps/api`, chosen by the club.
- It sits alongside the TypeScript apps and exposes REST/JSON with OpenAPI at `/v3/api-docs`, from which TS types can be generated.
- Ledger and auth logic stay in Postgres/Supabase as planned.
- The service's file-based snapshot store moves to Postgres `source_records`/`sync_runs` when the database lands.

**Rejected or deferred:**
- **Firebase/Firestore:** a document store makes ledger invariants and relational reporting harder.
- **A custom Node API server:** more to operate; Supabase plus Edge Functions covers it.
- **Flutter:** splits the language from the web admin.
- **A separate microservice per domain:** overkill at this scale.

## Repository layout
```
apps/mobile        Expo app (iOS/Android/web)
apps/admin         Next.js admin console
apps/api           Spring Boot (Java 21): SCA CSV sync + stats query API (ScaStatsController)
packages/domain    pure TS: money, ledger, allocation, paynow SGQR, stats, zod schemas
packages/db        generated Supabase types + query helpers
packages/ui        tokens → tailwind preset (+ shared RN primitives)
packages/config    eslint/tsconfig/prettier
supabase/          migrations, seed (synthetic), functions, tests (pgTAP)
design-system/     UI UX Pro Max output (MASTER.md + page overrides)
design/prototype/  static HTML prototype
docs/              specs
.github/workflows  ci.yml, db-backup.yml, eas-build.yml
```

## Environments
| Env | Supabase | Admin | Mobile | Data |
|---|---|---|---|---|
| local | `supabase start` (Docker) | `next dev` | Expo Go / dev client | synthetic seed |
| staging | Free project (may pause) | Vercel preview | EAS `preview` channel | synthetic + masked import tests |
| prod | **Pro**, `ap-southeast-1` | Vercel production | EAS `production` channel, stores | real |

## Server-side logic placement
| Logic | Where |
|---|---|
| Ledger mutations (confirm claim, allocate, adjust) | Postgres `security definer` functions + RLS |
| Balance/statement | SQL views/functions |
| Excel import parse + validate | Edge Function `import-validate` (SheetJS), then commit via SQL function |
| Bank CSV parse + match | Edge Function `bank-csv-match` |
| Notifications outbox | Edge Function `notify` on a cron schedule (pg_cron) |
| ICS feed | Edge Function `ics-feed` (token lookup) |
| Cricket sync | Edge Function per adapter, or admin-triggered upload |
| Stripe webhook (phase 5) | Edge Function `stripe-webhook` |

## CI pipeline (`ci.yml`)
1. Install (pnpm, frozen lockfile), then lint, typecheck and unit tests (Vitest) for every package.
2. Start Supabase, apply migrations, run **pgTAP** (RLS + ledger invariants).
3. Check that generated types are up to date (diff `supabase gen types`).
4. Build admin; run Expo `expo export` (web) as a smoke build.
5. Playwright e2e on admin + player web against local Supabase (critical paths).
6. CodeQL + dependency review.

## Operations
- **Backups:**
  - Supabase daily backups (Pro, 7 days).
  - `db-backup.yml` runs weekly: `pg_dump`, then `age` encryption, then private storage. It keeps 12 weekly and 12 monthly copies.
  - **Quarterly restore drill** into staging, with the result logged.
- **Monitoring:**
  - Sentry alerts on new issues.
  - Supabase log drain for function errors.
  - A free uptime monitor on the admin and ICS endpoints.
  - Daily digest email to the operator: failed jobs, unmatched bank lines, stale syncs.
- **Releases:**
  - Admin deploys on merge to `main`.
  - Mobile builds are tagged, with OTA updates for JS-only fixes.
  - Store builds ship for native changes.
- **Runbooks** (Phase 0): restore DB, rotate keys, revoke a lost device/session, reprocess a failed import, PDPA breach response.

## Multi-tenancy (future-proof, not SaaS)
- `club_id` on every tenant table.
- RLS checks club membership.
- Club config (name, colours, PayNow proxy, competitions) lives in `clubs.settings`, not in code.
- Only one club exists, and there is no club sign-up flow.
