# Hawks Cricket Club App — Claude Code Context

This repository contains the product and engineering context for building the Hawks Cricket Club application.

## Mission

Build a web + iOS + Android application for **Hawks Cricket Club (Hawks CC)** in Singapore.

The application should solve three operational problems first:

1. **Finance visibility and payments** — players need to know exactly what they owe, what they have paid, and what remains outstanding.
2. **Unified cricket data** — players need one place to see matches and player statistics across SCA Club League and tournaments such as BPL and IAT30.
3. **Communication and availability** — announcements, replies/reactions, match availability, notifications, calendar/travel planning, and feedback should move out of fragmented WhatsApp workflows.

The first deployment is **Hawks-only**. The architecture should be tenant-aware enough to support multiple cricket teams later, but do **not** prematurely build a complex SaaS/multi-tenant product.

## Read these files before planning

Read in this order:

1. `docs/00_README.md`
2. `docs/01_PRODUCT_VISION.md`
3. `docs/02_REQUIREMENTS.md`
4. `docs/03_ROLES_PERMISSIONS.md`
5. `docs/04_DOMAIN_MODEL.md`
6. `docs/05_FINANCE_PAYMENTS.md`
7. `docs/06_CRICKET_STATS_INTEGRATIONS.md`
8. `docs/07_COMMUNICATION_NOTIFICATIONS_CALENDAR.md`
9. `docs/08_UX_UI_DESIGN_SYSTEM.md`
10. `docs/09_TECH_ARCHITECTURE.md`
11. `docs/10_SECURITY_PRIVACY_AUDIT.md`
12. `docs/11_DATA_IMPORT_EXCEL.md`
13. `docs/12_TESTING_QA.md`
14. `docs/13_ROADMAP_MVP.md`
15. `docs/14_CLAUDE_CODE_BUILD_RULES.md`
16. `docs/15_OPEN_QUESTIONS.md`
17. `docs/SOURCES.md`

## Non-negotiable planning behaviour

Before implementing a major feature, use Claude Code's planning workflow to:

- inspect the existing repository;
- identify the relevant requirements and domain entities;
- identify assumptions and open decisions;
- state the smallest coherent implementation;
- define migrations, APIs, UI states, validation, error handling, permissions, tests, and observability;
- avoid building a feature from a UI-only perspective when it changes financial or cricket data.

## Critical principles

### 1. Finance is a ledger, not a single balance column

Never treat `outstanding_balance` as an authoritative mutable value.

Authoritative finance data should be based on immutable/auditable:
- charges,
- payment transactions,
- payment allocations,
- credits,
- refunds/adjustments.

Outstanding balance is calculated from the ledger.

### 2. Payment gateway data is not the club ledger

Stripe (or another gateway) is an external transaction source.

The application must maintain its own:
- invoice/charge records,
- payment records,
- allocation records,
- reconciliation status,
- audit history.

Use payment webhooks as the authoritative trigger for gateway state changes.

### 3. Cricket data is source-agnostic

Create a canonical match/statistics model. Keep the raw source response/export alongside normalized records.

Do not make the normalized model dependent on SCA URL shapes, BPL fields, or IAT30 fields.

### 4. Do not expose raw database access to normal management users

“Elevated database access” means elevated **application-level access** to finance/statistics/admin screens. Raw DB credentials must remain restricted to trusted technical operators.

### 5. Avoid brittle scraping unless explicitly justified

SCA currently exposes public pages and downloadable CSV/Excel exports, but no documented public API was identified during initial research. Design an integration adapter with:
- API path if an authorised/documented API exists,
- export/CSV ingestion path,
- carefully controlled web ingestion only if allowed and technically necessary.

### 6. Design mobile-first for players and responsive web-first for management

Players will frequently use phones.
Finance/statistics/admin workflows benefit from desktop tables and dashboards.

### 7. Build for safe failure

For every external integration:
- timeouts;
- retries with backoff;
- idempotency;
- deduplication;
- logging;
- alerting;
- manual retry;
- clear “last synced” state.

### 8. Do not invent missing data

A missing SCA/BPL/IAT30 value must remain unknown/null, not be inferred.

### 9. Preserve future multi-team capability without overengineering

Add a `club_id` / tenant reference to tenant-owned records and keep club configuration separate from application code. Deploy initially as one club.

### 10. Treat the Excel file as an input, not the long-term source of truth

The import process must be explicit, previewable, validated, reversible where practical, and idempotent.

## End of session

When the user writes **End session** (any capitalisation), run the `end-session` skill
(`.claude/skills/end-session/SKILL.md`). It writes an honest progress report as Obsidian-ready
Markdown to the user's vault (`HAWKS_OBSIDIAN_DIR`, default
`C:\Users\shrey\Documents\Obsidian\Hawks Cricket App\Progress Reports`), or hands it over as a
file in cloud sessions. Reports are never committed to this repo; only `docs/FEATURES.md` is updated.

## Stack decision (2026-10-04)

The club chose a **Spring Boot modular monolith (Java 21, Gradle) on PostgreSQL**, self-hosted in Docker on a near-free VM (Oracle Cloud Always Free). It lives in `backend/`; see `docs/09_TECH_ARCHITECTURE.md` and `backend/README.md`. Business rules and authorisation live in the Java modules, not in database policies. Keep module boundaries (`ModularityTests`), use Flyway for every schema change, and keep everything runnable and debuggable locally with `./gradlew bootRun` and `./gradlew test`. Mobile/web clients (Expo) call the REST API. The list below is the original evaluation, kept for context; where it conflicts with this decision, this decision wins.

## Recommended default stack (original evaluation)

Unless the repository already dictates otherwise, evaluate:

- Monorepo: pnpm + Turborepo
- Mobile: Expo + React Native + Expo Router
- Web: Next.js
- Shared packages: TypeScript types/domain logic/design tokens
- UI: Figma-driven design system, with a cross-platform component strategy
- Backend/data: PostgreSQL with a strong role/row-level authorization model; Supabase is a reasonable default for speed
- Auth: managed authentication with email/passwordless or magic-link support
- Payments: Stripe for cards + PayNow if the club is eligible for Stripe's Singapore setup
- Notifications: APNs/FCM through a managed push provider or Expo Notifications, plus transactional email
- File storage: object storage for exports/receipts
- Observability: structured logs + error tracking + audit logs
- CI/CD: GitHub Actions

Do not blindly adopt this stack. During planning, compare it with the repository and the developer's preferences.

## First planning gate: design tooling

Before irreversible UI architecture decisions, Claude should present the user with a short design tooling choice. See `docs/08_UX_UI_DESIGN_SYSTEM.md`.

The user specifically wants Claude to suggest where external design tools/plugins can be found and prompt for the preferred design workflow.

## First implementation gate

Do not start production payment integration or external cricket ingestion until:
- the domain model is approved;
- roles/permissions are defined;
- payment onboarding assumptions are confirmed;
- SCA access/integration method is confirmed;
- the Excel import sample has been inspected.

## Definition of done

A feature is not complete when the screen exists.

A feature is complete when, as applicable:
- database schema/migrations exist;
- API/service layer exists;
- authorization exists;
- empty/loading/error/success states exist;
- audit/observability exists for sensitive workflows;
- automated tests exist;
- mobile/web behaviour has been considered;
- documentation exists;
- external failures have a safe user experience.
