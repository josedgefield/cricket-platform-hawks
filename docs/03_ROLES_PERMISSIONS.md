# 03 — Roles & Permissions

## Roles (decided 2026-10-09)
Three roles. Each includes everything the roles below it can do.

| Role | Who | Scope |
|---|---|---|
| `player` | Every member | Own account, own statement and payments, club stats, notices |
| `admin` | Committee members who run the club day to day | All finance data; charges and records for all or specific players; announcements; stats imports; **manage players** (invite, edit, deactivate, reactivate) |
| `superuser` | Support (1–2 trusted people) | Everything an admin can do, plus **manage admins and superusers** and **change anyone's role** |
| *(technical operator)* | 1–2 developers | Not an app role. They hold infrastructure credentials (server, DB, secrets) under the break-glass rules below |

A member has exactly one role, stored on `members.role`. The earlier six-role design (captain, treasurer, stats_admin, comms_admin, club_admin) was replaced by these three.

## Joining and signing in
- **No self sign-up.** An admin or superuser invites someone by email. The link (single use, valid 7 days) lets them choose a password, and they are then signed in.
- **Sign-in:** email + password, at least 8 characters. Well-known passwords and ones built from the email are refused.
- **Forgotten password:** an emailed link, single use, valid 1 hour. Using it signs out every other device.
- **Sessions:** a revocable token per device. A session ends after 60 days without use for players and 14 days for admins and superusers. It also ends at once on sign-out, password change or reset, role change, or deactivation.
- **2FA:** authenticator-app (TOTP) for admins and superusers, required **before the finance module goes live**. Not built yet.
- **Brute force:** 5 wrong passwords lock that email for 15 minutes. This happens whether or not the account exists, so the lock reveals nothing about who is a member.
- **"Delete" = deactivate.** The member can't sign in, and their finance and stats history stays linked. It is reversible. Erasing personal data (PDPA) will be a separate, audited action.

## Permission matrix
✅ allowed · 👤 own records only · 👁 view only · — not allowed

| Capability | player | admin | superuser |
|---|---|---|---|
| View own statement / balance | 👤 | ✅ | ✅ |
| View all members' balances | — | ✅ | ✅ |
| Add charges, adjustments, payment records (all or specific players) | — | ✅ | ✅ |
| Submit payment claim | 👤 | ✅ | ✅ |
| View fixtures/results/stats | ✅ | ✅ | ✅ |
| Import/correct cricket data, link player names | — | ✅ | ✅ |
| Send announcements | — | ✅ | ✅ |
| React / reply | ✅ | ✅ | ✅ |
| Edit own name and phone, change own password | ✅ | ✅ | ✅ |
| List users | — | ✅ | ✅ |
| Invite, edit, deactivate, reactivate **players** | — | ✅ | ✅ |
| Invite, edit, deactivate, reactivate **admins and superusers** | — | 👁 | ✅ |
| Change roles (make someone an admin or superuser) | — | — | ✅ |
| Change own role or deactivate self | — | — | — |
| View audit log | — | ✅ (later) | ✅ (later) |

Extra rules:
- An invited member's email can be corrected before they accept, and a fresh invite then goes to the new address. After they join, the email is their sign-in, and only support can change it.
- The club always keeps at least one active superuser.

## Enforcement
1. **The Spring Boot application is the security boundary** (revised 2026-10-04, see `docs/09`). Every endpoint is denied by default and declares the roles it needs, scoped by `club_id`. The original RLS-based design is below for reference; the same role checks now live in Spring Security and service methods, with optional RLS later as defence in depth:
   - `auth_member_id()` returns the caller's member id;
   - `has_role(club_id, role)`;
   - `is_mfa()` checks the JWT `aal = 'aal2'`.
2. Finance writes require the `admin` role (superusers included) and, once built, a 2FA-verified session.
3. Ledger inserts that need to be atomic (confirm a claim → payment → allocations → audit) go through **SQL functions** (`confirm_payment_claim(...)`), not ad-hoc client inserts.
4. The UI hides what a role can't do, for usability only. It is never relied on for security.
5. **Authorisation tests** cover each role × endpoint × operation (see doc 12).

## Role changes
- Only superusers grant or remove the admin and superuser roles. Every change is recorded in `audit_log` with who did it and the old and new role.
- **Bootstrap:** a new server has no members. The operator sets `HAWKS_BOOTSTRAP_SUPERUSER_EMAIL`, and on first start that person is invited as the first superuser (audited). Local development instead creates a dev superuser with a known password.

## "Elevated database access" (CLAUDE.md §4)
- Management users get **application screens** with elevated permissions (finance console, stats corrections, audit viewer).
- Raw database credentials, server SSH access and the cloud console are limited to technical operators, with personal accounts and 2FA on the cloud provider and GitHub.
- **Break-glass:** any direct production SQL is done through a reviewed migration or a logged script, and announced to the committee.
