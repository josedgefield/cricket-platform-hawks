# Hawks CC Platform — Plan

> Status: **DRAFT v0.1 — for club review** · 2026-10-04
> Repo: `josedgefield/cricket-platform-hawks` (private)
> This is the master plan. The detailed reasoning lives in `docs/`, and the visual design lives in `design-system/` and `design/prototype/`.

---

## 1. Summary

Hawks CC needs one place where players can:
1. see **what they owe and what they've paid** (and pay it);
2. see **matches and stats** across SCA Club League, BPL and IAT30;
3. **communicate**: announcements, availability, notifications and calendar, without digging through WhatsApp.

**Recommendation in one line:**
- A TypeScript monorepo with **Expo** (iOS, Android and the player web app from one codebase) and a **Next.js** admin console.
- **Supabase Postgres in Singapore**, using an **append-only finance ledger** and **row-level security**.
- **Manual PayNow-QR payments with treasurer reconciliation** now, and **Stripe** once the club is a registered entity.

Estimated running cost is **about S$40/month** plus the Apple developer fee (US$99/yr).

### Facts confirmed by the club (2026-10-04)
| Topic | Answer | Consequence |
|---|---|---|
| Legal entity | Informal team, no UEN | Stripe is not available, so Phase 1 uses PayNow QR with manual reconciliation (§4) |
| Budget | Under S$50/month | Supabase Pro plus free tiers elsewhere (§6) |
| Users, first season | Under 50 | One Supabase project per environment; no scaling work |
| Design workflow | Code-first | Tokens in code plus Storybook; UI UX Pro Max skill vendored (§7) |
| Brand | Use estimate from logo | Navy `#1E2A78` + white, gold accent, Montserrat. All values are tokens and marked as estimates |
| Repo | Use this private repo | |

---

## 2. Evaluation of the concept

**What's strong**
- **The problem is clearly scoped.** Finance, cricket data and communication are real pain points with an obvious "before" (WhatsApp plus Excel).
- **CLAUDE.md already encodes good principles:** a ledger rather than a balance column, the gateway not being the ledger, source-agnostic stats, and safe failure.
- **The scale is small**, which allows simple infrastructure, fast iteration and a low operational burden.

**Risks and weak spots** (ranked)
1. **Money goes to a personal account (informal team).**
   - Risks: commingled funds, key-person dependency on the treasurer, no audit trail outside the app, and potential awkwardness with bank terms of service.
   - Mitigation: a dedicated account, treasurer confirmation workflow, audit log, and a monthly reconciliation report that a second person signs off. Long-term, register as a society to get a UEN, which also unlocks Stripe.
2. **Cricket data access is uncertain.** SCA has no documented API; BPL and IAT30 sources are unknown.
   - Mitigation: CSV/export ingestion first, manual entry as a fallback, and no scraping without permission (see `docs/06`).
3. **WhatsApp displacement is a behavioural problem, not a technical one.**
   - Mitigation: launch with the finance features (high value, private by nature), make push notifications reliable, and don't try to replace casual chat.
4. **App store overhead:** Apple review, account deletion requirement, Sign in with Apple, and privacy nutrition labels.
   - Mitigation: Expo EAS, plus the player **web app as a fallback** so nobody is blocked on store approval.
5. **Volunteer maintenance.** Club software dies when the one developer leaves.
   - Mitigation: managed services, no custom servers, documentation in the repo, and CLAUDE.md for AI-assisted maintenance.
6. **Personal data (PDPA).** The app holds phone numbers, emails, payment proofs and possibly minors' data. See §5.

**Things to deliberately NOT build in v1:** chat or DMs (keep WhatsApp for banter), live ball-by-ball scoring, multi-club SaaS onboarding, in-app card payments, a merchandise store.

---

## 3. Database management recommendations

**Engine:** PostgreSQL on **Supabase** in region `ap-southeast-1` (Singapore). It's relational, which suits a ledger; RLS gives row-level authorisation; and auth, storage, edge functions and backups come bundled.

### 3.1 Principles
| # | Rule |
|---|---|
| D1 | Every tenant-owned table has `club_id uuid not null` plus an index. Hawks is the single seeded club. |
| D2 | **Money is `bigint` cents + `currency char(3) default 'SGD'`.** Never floats. |
| D3 | **Ledger tables are append-only.** A trigger rejects `UPDATE`/`DELETE`. Corrections are made with reversal or adjustment rows that reference the original and record a reason. |
| D4 | Balances are **derived** (`member_balance` view or SQL function), never stored as authoritative. A materialised cache is allowed only if it's rebuildable. |
| D5 | Every table has `created_at`, `created_by`. Mutable tables also get `updated_at`, `updated_by`, and soft delete (`archived_at`) where history matters. |
| D6 | A generic `audit_log` trigger on sensitive tables records actor, action, table, row id, and before/after JSONB. |
| D7 | Cricket data is stored **raw plus normalised**: `source_records` (raw payload, source, fetched_at, sha256) → canonical tables. Unknown values stay NULL. |
| D8 | Imports are staged: `import_batches` → `import_rows` (validation status, messages) → commit. Each committed row records its `import_batch_id`, so a batch can be rolled back. |
| D9 | All schema changes go through **SQL migrations in the repo** (`supabase/migrations`), reviewed in PRs, with CI applying them to a throwaway database. No dashboard click-ops in prod. |
| D10 | Generated TypeScript types (`supabase gen types`) go into `packages/db`, and CI fails if they're stale. |

### 3.2 Core entities (see `docs/04_DOMAIN_MODEL.md` for fields)
- **Identity:** `clubs`, `members` (the person), `profiles` (auth link), `memberships` (member ↔ club, status, season), `role_assignments`
- **Finance:** `fee_schedules`, `charges`, `payment_claims` (the player's "I paid"), `payments` (treasurer-confirmed), `payment_allocations`, `adjustments` (credit, waiver, refund, reversal), `bank_statement_imports`, `gateway_events` (future)
- **Cricket:** `competitions`, `seasons`, `teams`, `fixtures/matches`, `innings`, `player_match_stats` (batting, bowling, fielding), `external_ids`, `source_records`, `sync_runs`
- **Community:** `announcements`, `announcement_reactions`, `announcement_replies`, `availability`, `selections`, `feedback`, `notification_prefs`, `push_tokens`, `calendar_feeds`
- **Platform:** `audit_log`, `import_batches`, `import_rows`, `consents`

### 3.3 Environments, backups, access
- **Environments:** `local` (Supabase CLI + Docker), `staging` (a free project, OK to pause), `prod` (Pro). Only synthetic seed data outside prod.
- **Backups:** Pro daily backups (7-day retention), plus a **weekly GitHub Action `pg_dump` → age-encrypted → private object storage**. A **restore drill each quarter** is documented in `docs/09`.
- **Access:** raw DB and service-role credentials go to at most 2 technical operators. Management users get **application-level** elevated screens only (per CLAUDE.md principle 4).

---

## 4. Payment management recommendations

### 4.1 Phase 1: no gateway (informal team)
```
Treasurer raises charges ──► Player sees itemised balance
                                   │
                                   ▼
                    "Pay with PayNow" → SGQR with fixed amount
                    + unique reference (e.g. HWK-26-0042)
                                   │  pays in their own banking app
                                   ▼
                    "I've paid" (+ optional screenshot) → payment_claim [PENDING]
                                   │
       Treasurer: confirm manually ◄┴► or upload bank CSV → auto-match by ref+amount
                                   │
                                   ▼
                    payment [CONFIRMED] + allocations → balance updates → receipt email
```
- **Nothing is "paid" until the treasurer confirms it.** The player sees "Pending confirmation" in the meantime.
- **Partial payments, overpayments (credit), waivers and refunds** are all ledger rows. Allocation goes oldest charge first by default, and the treasurer can override.
- **Cash and bank transfers** are recorded by the treasurer as payments with a method and a note.
- **Reminders:** due-soon and overdue notices (push plus email) are rate-limited, and the player can see their reminder history.
- **Reports:** an outstanding-by-member report, a monthly reconciliation pack (CSV/PDF), and an audit trail of who confirmed what.

**Recommendations to the club:**
1. Open a **dedicated bank account** for club money, even a personal account used only for the club, and register PayNow on it.
2. Have a **second person (e.g. captain or president) review** the monthly reconciliation report.
3. Consider **registering as a society (Registry of Societies)** to get a UEN. That enables a club bank account and Stripe (§4.2).

### 4.2 Phase 5: Stripe (after the club has a UEN)
- A `PaymentGateway` interface in `packages/domain`. The Stripe adapter uses Checkout or Payment Intents with **PayNow + cards**.
- The **webhook is authoritative.** The signature is verified, the event is stored in `gateway_events` (unique on event id, so it's idempotent), and processing creates `payments`/`allocations` in the **same ledger**.
- Every create call carries an idempotency key, and a nightly job reconciles Stripe balance transactions against the ledger.
- Indicative fees: PayNow ~1.3%; local cards ~3.4% + S$0.50 (**verify at onboarding**). Option: pass fees on or absorb them, configured per fee schedule.
- No card data ever touches our servers (Stripe-hosted), so the PCI scope is SAQ-A.

### 4.3 App store note
Club fees pay for a **real-world service**, so Apple and Google rules allow external payment rather than in-app purchase (Apple App Review Guideline 3.1.3(e)). This is **to be re-verified before submission.**

Details: `docs/05_FINANCE_PAYMENTS.md`.

---

## 5. Security and privacy recommendations

| Area | Recommendation |
|---|---|
| **Authentication** | Supabase Auth: email OTP/magic link + Sign in with Apple + Google. **Invite-only** (admin adds the member, member claims via email). No public sign-up. |
| **MFA** | **TOTP MFA required** for `treasurer` and `club_admin`; finance and admin routes check `aal2`. |
| **Authorisation** | RLS on **every** table, default deny. Policies use `has_role(club_id, role)` helper functions. **pgTAP tests per policy in CI.** UI hiding is never the security boundary. |
| **Roles** | `player`, `captain`, `treasurer`, `stats_admin`, `comms_admin`, `club_admin`. Scoped per club and optionally per team. See `docs/03`. |
| **Secrets** | The service-role key only in Edge Functions and the Next.js server runtime, never in Expo bundles or `NEXT_PUBLIC_*`. Secrets live in GitHub/Vercel/EAS secret stores. `.env*` is git-ignored. |
| **Files** | Payment screenshots and exports go in **private buckets**, served by short-lived signed URLs. MIME and size allow-list (JPEG/PNG/PDF, ≤5MB). EXIF stripped. Path is `club_id/member_id/…` with RLS on storage. |
| **Mobile** | Tokens in `expo-secure-store`. Push payloads contain **no PII or amounts** ("You have a new club notice"). Deep links validated. Optional biometric lock on the Money tab. |
| **Web** | CSP, HSTS, `frame-ancestors 'none'`, SameSite cookies, CSRF protection on server actions, rate limiting on auth/OTP and claims. |
| **Supply chain** | Dependabot, CodeQL, GitHub secret scanning + push protection, lockfile committed, pinned GitHub Actions, branch protection on `main` (PR + green CI). |
| **Audit** | `audit_log` for finance, roles, imports and member data. Admins can view it but not edit it. Retained 7 years for finance (to be confirmed). |
| **Observability** | Sentry (errors, with PII scrubbing), structured logs, and an alert on webhook/import/sync failures. |
| **PDPA (Singapore)** | Purpose-specific consent at onboarding, privacy notice, a named **Data Protection Officer** contact, data minimisation (no NRIC), access/correction/deletion request flow, retention schedule, and a **breach response plan: notify PDPC within 3 calendar days of assessing a breach as notifiable**. Parental consent if there are under-18 members. |
| **Store compliance** | In-app account deletion (Apple requirement), privacy nutrition labels, data safety form (Google). |
| **Sensitive inputs** | The Excel workbook and bank CSVs are **never committed** (`.gitignore`). They're processed in memory or in staging tables and deleted after import. |
| **External data** | SCA/BPL/IAT30 are accessed only by permitted means. Respect robots/ToS; no credential sharing. |

Details: `docs/10_SECURITY_PRIVACY_AUDIT.md`.

---

## 6. Architecture and stack

```
apps/
  mobile/      Expo SDK + Expo Router + NativeWind  → iOS, Android, and player web (expo export)
  admin/       Next.js (App Router) + Tailwind       → treasurer/admin desktop console
packages/
  domain/      ledger maths, allocation, PayNow SGQR builder, zod schemas (pure TS, fully unit tested)
  db/          generated Supabase types + typed query helpers
  ui/          design tokens (from design-system/MASTER.md) → Tailwind/NativeWind preset
  config/      eslint, tsconfig, prettier
supabase/
  migrations/  SQL migrations, RLS policies, triggers
  functions/   Edge Functions: notify, import-commit, bank-csv-match, ics-feed, (later) stripe-webhook
  tests/       pgTAP RLS + ledger tests
design-system/ generated + adapted design system (UI UX Pro Max)
design/prototype/ static clickable HTML prototype
docs/          specifications
```

| Concern | Choice | Monthly cost |
|---|---|---|
| DB/Auth/Storage/Functions | Supabase Pro (SG region) | ~US$25 |
| Admin web hosting | Vercel Hobby (upgrade if club use breaches the ToS) | $0 |
| Mobile builds/updates | Expo EAS free tier | $0 |
| Email | Resend free tier (3k/month) | $0 |
| Push | Expo Notifications (APNs/FCM) | $0 |
| Errors | Sentry Developer | $0 |
| CI | GitHub Actions (private repo minutes) | $0 at this scale |
| Store accounts | Apple US$99/yr, Google US$25 once | ~S$11/mo amortised |

**Why Expo for player web as well as Next.js admin?** Players get identical UX on every device from one codebase. Treasurer workflows (dense tables, CSV upload, bulk actions) are much better on Next.js. Both share `packages/domain` and `packages/ui`.

Details: `docs/09_TECH_ARCHITECTURE.md`.

---

## 7. Design approach (code-first + UI UX Pro Max)

- The **UI UX Pro Max** skill is vendored at `.claude/skills/ui-ux-pro-max` (MIT, pinned commit; see `VENDORED.md`). It was security-reviewed before use: Python standard library only, no network or subprocess calls.
- Generated design system: `design-system/hawks-cricket-club/MASTER.md`. Hawks overrides are documented in it: navy + talon gold, Montserrat/Barlow, athletic minimalism, and the triangle motif.
- **Clickable prototype:** `design/prototype/index.html` (public site) plus app screens: player home, money, matches & stats, and treasurer reconciliation.
- In implementation, tokens move into `packages/ui` and components are reviewed in **React Native Storybook** (web build), per the code-first choice.

Details: `docs/08_UX_UI_DESIGN_SYSTEM.md`.

---

## 8. Roadmap

Each phase is done only when it meets CLAUDE.md's *Definition of done*: migrations, API, authorisation, all UI states, audit, tests, docs, and safe failure.

| Phase | Scope | Key deliverables | Gate to start |
|---|---|---|---|
| **0. Foundations** (2–3 wks) | Monorepo, CI, Supabase local/staging/prod, auth (invite-only, MFA for admins), roles + RLS helpers, audit log, tokens + Storybook, app shells | `pnpm dev` runs everything; CI covers lint/type/test/pgTAP; empty authenticated shells on iOS, Android and web | This plan approved |
| **1. Finance MVP** (4–5 wks) | Fee schedules, charges (bulk), ledger + balance view, statement, PayNow SGQR, payment claims, treasurer confirm, bank CSV matcher, adjustments, reminders, Excel import (preview → commit → rollback), admin console | Treasurer runs one real billing cycle end to end in staging with masked data | Excel sample inspected; fee structure confirmed; PayNow account decided |
| **2. Matches & comms** (3–4 wks) | Fixtures (manual + import), availability, selection, announcements with replies/reactions, push + email, notification prefs | Captain picks an XI from availability; announcements reach everyone by push | Roles confirmed |
| **3. Stats** (3–4 wks) | Canonical stats model, SCA CSV adapter, BPL/IAT30 adapters (method TBC), sync runs with "last synced", player profile + leaderboards + charts | A season's stats match the source exports, verified by reconciliation tests | SCA/BPL/IAT30 access method confirmed |
| **4. Calendar, feedback, launch** (2–3 wks) | Per-user ICS feed (signed URL), travel/venue info, feedback forms, privacy/consent flows, account deletion, store submission | Live in App Store + Play Store; web live | PDPA notice approved |
| **5. Later** | Stripe (needs a UEN), multi-team config, public website CMS, sponsor pages | | Club registered |

---

## 9. Implementation gates (from CLAUDE.md)

Production payment and external ingestion work does **not** start until all of these are done:
- [ ] Domain model approved (`docs/04`)
- [ ] Roles/permissions approved (`docs/03`)
- [ ] Payment onboarding assumptions confirmed (PayNow recipient, dedicated account)
- [ ] SCA/BPL/IAT30 access method confirmed
- [ ] Excel import sample inspected (with masked personal data)

---

## 10. Open questions (full list in `docs/15_OPEN_QUESTIONS.md`)

1. Can you share the Excel workbook, with names and phone numbers masked if preferred?
2. What is the fee structure: season fee, per-match fee, nets, kit, tournament entry? Due dates? Late fees?
3. Who is treasurer? Will there be a dedicated bank or PayNow account?
4. Are any members under 18?
5. How do you get SCA data today (logins, exports)? Where do BPL and IAT30 data come from?
6. Who holds the admin, captain and stats roles?
7. What is the refund, waiver and hardship policy?
8. What should stay on WhatsApp?
9. Do you have official brand hex codes, a font, and an SVG logo?

---

## 11. Requirement traceability (CLAUDE.md)

| CLAUDE.md principle | Where addressed |
|---|---|
| 1. Ledger not balance | §3.1 D2–D4, `docs/04`, `docs/05` |
| 2. Gateway ≠ ledger, webhooks authoritative | §4.2, `docs/05` |
| 3. Source-agnostic cricket data | §3.1 D7, `docs/06` |
| 4. No raw DB access for management | §3.3, `docs/03`, `docs/10` |
| 5. Avoid brittle scraping | §2 risk 2, `docs/06` |
| 6. Mobile-first players / web-first management | §6, `docs/08` |
| 7. Safe failure | `docs/06`, `docs/09` (integration checklist) |
| 8. Do not invent missing data | §3.1 D7, `docs/06` |
| 9. Tenant-aware, not SaaS | §3.1 D1, `docs/09` |
| 10. Excel as input | §3.1 D8, `docs/11` |
| Design tooling gate | §7: code-first chosen, `docs/08` |
| Definition of done | §8, `docs/12`, `docs/14` |
