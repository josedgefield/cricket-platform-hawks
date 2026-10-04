# 03 — Roles & Permissions

## Roles
| Role | Who | Scope |
|---|---|---|
| `player` | Every active member | Own data and club-wide public-to-members data |
| `captain` | Captain / vice-captain | Their team's fixtures, availability, selection, team announcements |
| `treasurer` | Treasurer (+ assistant) | All finance data. **MFA required** |
| `stats_admin` | Scorer / stats volunteer | Cricket data imports, mappings, corrections |
| `comms_admin` | Committee member | Announcements to any audience |
| `club_admin` | President / secretary | Members, roles, settings, audit log view. **MFA required** |
| *(technical operator)* | 1–2 developers | Not an app role. They hold infrastructure credentials (server, DB, secrets) under the break-glass rules below |

A member can hold several roles. Roles are stored in `role_assignments(club_id, member_id, role, team_id null, granted_by, granted_at, revoked_at)`.

## Permission matrix
✅ allowed · 👤 own records only · 👥 own team only · — not allowed

| Capability | player | captain | treasurer | stats_admin | comms_admin | club_admin |
|---|---|---|---|---|---|---|
| View own statement / balance | 👤 | 👤 | ✅ | 👤 | 👤 | 👤 |
| View all members' balances | — | — | ✅ | — | — | ✅ (read) |
| Raise/void charges, adjustments | — | — | ✅ | — | — | — |
| Confirm/reject payment claims | — | — | ✅ | — | — | — |
| Submit payment claim | 👤 | 👤 | ✅ | 👤 | 👤 | 👤 |
| View fixtures/results/stats | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Set own availability | 👤 | 👤 | 👤 | 👤 | 👤 | 👤 |
| View squad availability | 👥 | 👥 | — | — | — | ✅ |
| Publish selection | — | 👥 | — | — | — | ✅ |
| Import/correct cricket data | — | — | — | ✅ | — | ✅ |
| Post announcement (all-club) | — | — | — | — | ✅ | ✅ |
| Post announcement (team) | — | 👥 | — | — | ✅ | ✅ |
| React / reply | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Invite members / edit profiles | — | — | — | — | — | ✅ |
| Grant/revoke roles | — | — | — | — | — | ✅ (cannot self-grant `treasurer` without a second admin — see below) |
| Run Excel import | — | — | ✅ (finance sheets) | ✅ (stats sheets) | — | ✅ |
| View audit log | — | — | ✅ (finance) | — | — | ✅ |

> ASSUMPTION: Other members' balances are **private** and visible only to the treasurer and club admins, not to captains. Please confirm.

## Enforcement
1. **The Spring Boot application is the security boundary** (revised 2026-10-04, see `docs/09`). Every endpoint is denied by default and declares the roles it needs, scoped by `club_id`. The original RLS-based design is below for reference; the same role checks now live in Spring Security and service methods, with optional RLS later as defence in depth:
   - `auth_member_id()` returns the caller's member id;
   - `has_role(club_id, role [, team_id])`;
   - `is_mfa()` checks the JWT `aal = 'aal2'`.
2. Finance write policies require `has_role(club_id,'treasurer') AND is_mfa()`.
3. Ledger inserts that need to be atomic (confirm a claim → payment → allocations → audit) go through **SQL functions** (`confirm_payment_claim(...)`), not ad-hoc client inserts.
4. The UI hides what a role can't do, for usability only. It is never relied on for security.
5. **Authorisation tests** cover each role × endpoint × operation (see doc 12).

## Sensitive role changes
- Granting `treasurer` or `club_admin` requires a second `club_admin` approval (two-person rule). This is recorded in `audit_log`.
- If there is only one admin at bootstrap, the technical operator seeds the first admins through a migration seed script. That seeding is itself audited.

## "Elevated database access" (CLAUDE.md §4)
- Management users get **application screens** with elevated permissions (finance console, stats corrections, audit viewer).
- Raw database credentials, server SSH access and the cloud console are limited to technical operators, with personal accounts and 2FA on the cloud provider and GitHub.
- **Break-glass:** any direct production SQL is done through a reviewed migration or a logged script, and announced to the committee.
