---
type: feature-list
project: Hawks CC platform
updated: 2026-10-05
last_report: 2026-10-05
last_report_head: e8e1d7a
tags:
  - hawks-cc
  - features
---

# Hawks CC — Features to build

Build top to bottom; each phase ends with a demo to the committee (`docs/13_ROADMAP_MVP.md`).
Requirement IDs refer to `docs/02_REQUIREMENTS.md`, Q numbers to `docs/15_OPEN_QUESTIONS.md`.
Status values: Done · Partly done · Not started · Blocked · Dropped. Updated by the `end-session` skill;
progress reports themselves live in the Obsidian vault, not in this repo.

## Already done

| Feature | Phase | Notes |
| --- | --- | --- |
| Plan, specs (docs 00–15), design system, clickable prototype | pre-0 | `plan.md`, `docs/`, `design/prototype/` |
| Spring Boot backend foundation: modules, Flyway, security skeleton, CORS, CI, Docker | 0 | `backend/`, all tests pass in CI |
| Expo app shell: tabs, design tokens, loading/error/empty states, CI with browser smoke test | 0 | `apps/mobile/` |

## To build

| # | Feature | Phase | Requirement | Status | Waiting on |
| --- | --- | --- | --- | --- | --- |
| 1 | Deploy backend and web app to the free VM | 0 | NF-3, NF-7 | Not started | A VM and a domain |
| 2 | Member sign-in: invites, email codes, profiles, no self sign-up | 0 | ID-1, ID-2, ID-3 | Not started | Role holders named (Q6) |
| 3 | Roles, admin two-factor login, audit log | 0 | NF-1, NF-6 | Not started | — |
| 4 | Error tracking, nightly backups, restore drill | 0 | NF-3 | Partly done | Backup script drafted, not running |
| 5 | Season memberships | 1 | ID-4 | Not started | — |
| 6 | Fee schedules and charges | 1 | FI-1, FI-2 | Blocked | Fee structure (Q2) |
| 7 | Ledger: statement, balance, allocations, adjustments | 1 | FI-3, FI-8, FI-9 | Not started | — |
| 8 | PayNow QR, "I've paid" claims, treasurer confirmation | 1 | FI-4, FI-5, FI-6 | Blocked | PayNow recipient account (Q3) |
| 9 | Excel import of members and opening balances | 1 | docs/11 | Blocked | Masked Excel sample (Q1) |
| 10 | Bank statement matching | 1 | FI-7 | Blocked | Which bank (Q3) |
| 11 | Finance reports and payment reminders | 1 | FI-10, FI-11 | Not started | — |
| 12 | Fixtures and results | 2 | CR-1 | Not started | Where fixtures come from |
| 13 | Availability and XI selection | 2 | CR-2, CR-3 | Not started | — |
| 14 | Announcements with replies and reactions | 2 | CO-1, CO-2 | Not started | — |
| 15 | Push and email notifications, with preferences | 2 | CO-3 | Not started | — |
| 16 | Stats import, API and app Stats tab | 3 | CR-4, CR-5, CR-6 | Partly done | Bundled data is BPL 2025 only |
| 17 | Stats import screen with preview | 3 | CR-5 | Not started | — |
| 18 | Link one player's names across sources | 3 | CR-4 | Not started | — |
| 19 | Audited stats corrections | 3 | CR-7 | Not started | — |
| 20 | SCA stats | 3 | CR-5 | Partly done | Imports work (copied tables, `backend/scripts/import-sca.sh`); data only in a local database, not bundled |
| 21 | Scheduled sync from export links | 3 | CR-5 | Blocked | SCA permission (Q19) |
| 22 | Match scorecards, player profiles, charts | 3 | CR-4, CR-8 | Not started | Scorecards from SCA and CricHeroes |
| 23 | Calendar feed and venue info | 4 | CO-4, CO-5 | Not started | — |
| 24 | Feedback form | 4 | CO-6 | Not started | — |
| 25 | Privacy notice, consents, account deletion | 4 | ID-5, WEB-2 | Not started | DPO named (Q13), consent for public names (Q22) |
| 26 | Public club website | 4 | WEB-1 | Not started | Domain (Q15) |
| 27 | App Store and Play Store release | 4 | NF-9 | Not started | Developer accounts (Q16) |
| 28 | Card and PayNow payments via Stripe | 5 | FI-12 | Blocked | Club registration with a UEN (Q14) |
