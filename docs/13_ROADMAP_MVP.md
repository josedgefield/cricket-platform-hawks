# 13 — Roadmap & MVP

**MVP = Phases 0–2:** auth, finance, matches and comms.

Stats (Phase 3) follows as soon as data access is confirmed. Durations assume one part-time developer working with Claude Code, and are indicative only.

## Phase 0 — Foundations (2–3 weeks)
- Monorepo scaffold (pnpm + Turborepo), lint/format/typecheck, CI pipeline, Dependabot/CodeQL, branch protection.
- Spring Boot backend (✅ skeleton + stats module), deployment to the Oracle VM; Flyway migrations for members, memberships, role_assignments, consents and audit_log; authorisation rules + role tests.
- Auth: invite flow, OTP/magic link, Apple/Google, MFA enrolment for admins.
- `packages/ui` tokens from MASTER.md; Storybook; app shells (tabs, admin sidebar) with all states.
- Sentry, logging, runbooks (restore, key rotation).
- **Exit:** a new member can be invited, log in on iOS, Android and web, and see an empty Home. CI is green, including authorisation tests.

## Phase 1 — Finance MVP (4–5 weeks)
- Fee schedules, bulk charges, ledger tables + triggers, balance/statement functions.
- Money tab: balance, open charges, PayNow SGQR sheet, claim + proof upload, statement.
- Admin: claims queue, record payment, adjustments with reasons, bank CSV import + matcher, reports, reconciliation pack.
- Excel import framework + finance/member mappings; cut-over procedure.
- Reminders (outbox + push/email).
- **Exit:** all finance acceptance scenarios in doc 12 pass, and one dry-run billing cycle with the treasurer on staging gets sign-off.

## Phase 2 — Matches & Communication (3–4 weeks)
- Competitions, seasons, teams, venues, fixtures (manual + CSV).
- Availability, selection, notifications.
- Announcements with audiences, reactions, replies; notification preferences; in-app inbox.
- **Exit:** a real match week runs through the app: fixture → availability → XI published → notifications received.

## Phase 3 — Stats (3–4 weeks, after data access is confirmed)
- Canonical stats model, source_records, sync_runs, alias mapping queue, overrides.
- SCA export adapter; CricHeroes CSV/paste adapter for BPL (see `docs/06`); IAT30 as access allows.
- Player profiles, leaderboards, charts, "last synced".
- **Exit:** season totals reconcile with the sources.

## Phase 4 — Calendar, Feedback, Launch (2–3 weeks)
- ICS feeds, venue/travel info, feedback.
- Privacy notice, consents, data export/deletion requests, account deletion.
- Public website (landing, fixtures/results, join, sponsors).
- App Store / Play Store submission; launch comms to members.
- **Exit:** live in both stores, with ≥ 90% of members onboarded within 4 weeks.

## Phase 5 — Later
- Stripe (PayNow + cards) after registration (UEN).
- Multi-team configuration; sponsor management; public site CMS.
- Optional: live scoring integration, merchandise.

## Milestone review
After each phase there's a demo to the committee and a retro. The roadmap and docs are updated, and doc 15 is re-checked.
