# 02 — Requirements

Priority: **M**ust / **S**hould / **C**ould / **W**on't (v1). The phase number refers to [13_ROADMAP_MVP](13_ROADMAP_MVP.md).

## Functional

### Identity & membership
| ID | Requirement | P | Phase |
|---|---|---|---|
| ID-1 | Admin invites a member by email. The member claims the account via OTP/magic link, Apple or Google | M | 0 |
| ID-2 | No public self sign-up | M | 0 |
| ID-3 | Member profile: name, display name, email, mobile (optional), playing role, batting/bowling style, photo (optional) | M | 0 |
| ID-4 | Memberships per season with a status (active, inactive, guest, alumni) | M | 1 |
| ID-5 | In-app account deletion request (store requirement), handled under the finance retention rules | M | 4 |

### Finance
| ID | Requirement | P | Phase |
|---|---|---|---|
| FI-1 | Treasurer defines fee schedules (season, per match, nets, kit, tournament) | M | 1 |
| FI-2 | Treasurer raises charges for one member or in bulk (e.g. everyone in the season squad) | M | 1 |
| FI-3 | Player sees an itemised statement: charges, payments, adjustments, running balance | M | 1 |
| FI-4 | Player can pay via a generated PayNow QR (fixed amount + reference) | M | 1 |
| FI-5 | Player can submit "I've paid" with an optional screenshot; it stays pending until confirmed | M | 1 |
| FI-6 | Treasurer confirms or rejects claims, and records cash/bank transfers | M | 1 |
| FI-7 | Treasurer uploads a bank statement CSV; the system proposes matches by reference and amount | S | 1 |
| FI-8 | Adjustments: waiver, credit, refund, reversal, each with a mandatory reason | M | 1 |
| FI-9 | Allocation of payments to charges (oldest first by default, can be overridden) | M | 1 |
| FI-10 | Reminders for due-soon and overdue charges (push + email), rate-limited | S | 1 |
| FI-11 | Reports: outstanding by member, collections by period, monthly reconciliation pack (CSV) | M | 1 |
| FI-12 | Card/PayNow gateway (Stripe) | W (until UEN) | 5 |

### Cricket
| ID | Requirement | P | Phase |
|---|---|---|---|
| CR-1 | Fixtures and results across competitions (SCA league, BPL, IAT30, friendlies) | M | 2 |
| CR-2 | Availability per fixture (Available / Maybe / Unavailable + note) | M | 2 |
| CR-3 | Captain selects the XI and publishes it; players are notified | S | 2 |
| CR-4 | Per-player batting/bowling/fielding stats, by season and competition, plus career totals | M | 3 |
| CR-5 | Import from SCA exports (CSV/Excel); adapters for BPL/IAT30 | M | 3 |
| CR-6 | "Last synced" and source shown on every stats view | M | 3 |
| CR-7 | Manual correction by stats admin, audited, without overwriting the raw source | M | 3 |
| CR-8 | Leaderboards and charts | S | 3 |

### Communication
| ID | Requirement | P | Phase |
|---|---|---|---|
| CO-1 | Announcements with audience (all, squad, committee), pinned items, attachments | M | 2 |
| CO-2 | Reactions and threaded replies on announcements | S | 2 |
| CO-3 | Push + email notifications with per-category preferences | M | 2 |
| CO-4 | Personal calendar feed (ICS) of the member's fixtures, training and due dates | S | 4 |
| CO-5 | Venue info with map link and travel notes | S | 4 |
| CO-6 | Feedback form (to committee), optionally anonymous | C | 4 |

### Public website
| ID | Requirement | P | Phase |
|---|---|---|---|
| WEB-1 | Landing page: hero, upcoming fixtures, latest results, about, join us, sponsors, contact | S | 4 |
| WEB-2 | Shows no personal data beyond what members have consented to (e.g. squad names/photos) | M | 4 |

## Non-functional
| ID | Requirement |
|---|---|
| NF-1 | **Security:** default-deny authorisation in the API, MFA for finance/admin, OWASP ASVS L1 baseline (see doc 10) |
| NF-2 | **Privacy:** PDPA compliant; data stored in Singapore region |
| NF-3 | **Availability:** best-effort 99.5%; no data loss beyond 24h (RPO) and restore within 1 day (RTO) |
| NF-4 | **Performance:** player home loads in under 2s on 4G; admin tables in under 1s for 1k rows |
| NF-5 | **Accessibility:** WCAG 2.2 AA; touch targets ≥ 44pt; supports Dynamic Type |
| NF-6 | **Auditability:** every finance/role/import mutation recorded with the actor |
| NF-7 | **Cost:** under S$50/month running cost |
| NF-8 | **Maintainability:** typed end to end, CI-enforced, documented, runnable locally with one command |
| NF-9 | **Platforms:** iOS (last 2 major versions), Android 10+, evergreen browsers |

> ASSUMPTION: The priorities are proposed and need review by the committee.
