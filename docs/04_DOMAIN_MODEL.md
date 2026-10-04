# 04 — Domain Model

Conventions:
- Every table has `id uuid pk default gen_random_uuid()`, `club_id uuid not null references clubs` (except `clubs`), `created_at timestamptz default now()`, `created_by uuid`.
- Money is stored as `amount_cents bigint` + `currency char(3) default 'SGD'`.
- 🔒 marks an append-only table (UPDATE/DELETE blocked by trigger).

## Entity overview

```
clubs ─┬─ members ── profiles (auth.users)
       ├─ memberships (member × season)
       ├─ role_assignments
       ├─ teams ── competitions ── seasons
       │            └─ matches ─┬─ innings
       │                        ├─ player_match_stats
       │                        ├─ availability
       │                        └─ selections
       ├─ fee_schedules
       ├─ charges 🔒 ─────────┐
       ├─ payment_claims      │
       ├─ payments 🔒 ── payment_allocations 🔒
       ├─ adjustments 🔒 ─────┘
       ├─ bank_statement_imports ── bank_statement_lines
       ├─ announcements ── replies / reactions
       ├─ source_records · external_ids · sync_runs
       ├─ import_batches ── import_rows
       └─ audit_log 🔒 · consents · notification_prefs · push_tokens · calendar_feeds
```

## Identity
| Table | Key fields |
|---|---|
| `clubs` | name, short_name, slug, timezone (`Asia/Singapore`), currency, settings jsonb (PayNow proxy, branding) |
| `members` | full_name, display_name, email (unique per club), mobile (nullable), date_of_birth (nullable — see open questions), playing_role, batting_style, bowling_style, photo_path, status |
| `profiles` | `user_id` (auth.users) ↔ `member_id`; one auth user per member per club |
| `memberships` | member_id, season_id, type (full, student, social, guest), status, joined_at, left_at |
| `role_assignments` | member_id, role, team_id (nullable), granted_by, approved_by, granted_at, revoked_at |
| `consents` | member_id, purpose (privacy_notice, photos_public, marketing), version, granted_at, withdrawn_at |

## Finance
| Table | Key fields | Notes |
|---|---|---|
| `fee_schedules` | name, kind (season/match/nets/kit/tournament/other), default_amount_cents, season_id, due_rule | Template only; doesn't change existing charges |
| `charges` 🔒 | member_id, fee_schedule_id (nullable), description, amount_cents (>0), due_date, reference (unique, e.g. `HWK-26-0042`), match_id (nullable), import_batch_id (nullable) | Voiding = an `adjustments` row of kind `void` |
| `payment_claims` | member_id, amount_cents, method (paynow/bank/cash), reference, claimed_at, proof_path (nullable), status (pending/confirmed/rejected), reviewed_by, reviewed_at, reason | Mutable status; not part of the ledger |
| `payments` 🔒 | member_id, amount_cents (>0), method, received_on, external_ref (bank ref / Stripe id), claim_id (nullable), bank_line_id (nullable), confirmed_by | The authoritative receipt |
| `payment_allocations` 🔒 | payment_id, charge_id, amount_cents (>0) | Σ allocations per payment ≤ payment amount |
| `adjustments` 🔒 | member_id, kind (waiver/credit/refund/void/reversal), amount_cents (signed per kind), charge_id/payment_id ref, reason (required), approved_by | |
| `bank_statement_imports` | uploaded_by, file_hash (unique), period_from/to, row_count | The file is deleted after parsing |
| `bank_statement_lines` | import_id, posted_on, amount_cents, description, parsed_reference, match_status, matched_payment_id | Unique on (club, posted_on, amount, description hash) to dedupe |
| `gateway_events` *(phase 5)* | provider, event_id (unique), type, payload jsonb, processed_at, error | Webhook idempotency |

**Derived (views/functions):**
- `member_balance(member_id)` = Σ charges − Σ waiver/void/credit + Σ refunds − Σ payments. A positive result means the member owes money.
- `charge_status(charge_id)` is one of `open`, `part_paid`, `paid`, `waived` or `void`, plus whether it is overdue.
- `member_statement(member_id)` is the chronological ledger with a running balance.

**Invariants (tested):**
- The balance always equals the sum of the ledger.
- No allocation exceeds the charge's remaining amount or the payment's unallocated amount.
- References are unique per club.
- Every adjustment has a reason and an approver.

## Cricket (source-agnostic)
| Table | Key fields |
|---|---|
| `competitions` | name, organiser (SCA/BPL/IAT30/friendly/other), format (T20/40-over/50-over/multi-day/T30), level/division |
| `seasons` | competition_id, label (e.g. 2026), starts_on, ends_on |
| `teams` | name, is_own_team (bool), short_name — opponents are teams too |
| `venues` | name, address, lat/lng, notes, map_url |
| `matches` | season_id, home_team_id, away_team_id, venue_id, starts_at, status (scheduled/live/completed/abandoned/cancelled), result_text, winner_team_id (nullable), toss fields (nullable) |
| `innings` | match_id, batting_team_id, number, runs, wickets, overs_bowled (stored as balls), extras jsonb |
| `player_match_stats` | match_id, member_id (nullable), external_player_name, team_id, **batting** (runs, balls, fours, sixes, how_out, position), **bowling** (balls, maidens, runs, wickets, wides, no_balls), **fielding** (catches, stumpings, run_outs) — *all nullable* |
| `external_ids` | entity_type, entity_id, source (sca/bpl/iat30), external_id — unique (source, entity_type, external_id) |
| `source_records` | source, kind (match/scorecard/player/fixture list), external_id, fetched_at, content_sha256, raw jsonb/text, sync_run_id |
| `sync_runs` | source, method (csv/api/manual), started_at, finished_at, status, counts, error |
| `player_aliases` | member_id, source, name_as_seen — maps a source name to a member |

## Community
| Table | Key fields |
|---|---|
| `availability` | match_id, member_id, status (yes/maybe/no), note, updated_at — unique (match, member) |
| `selections` | match_id, member_id, role_in_xi (captain/wk/12th), published_at |
| `announcements` | author_id, audience (all/team/committee), team_id, title, body (markdown), pinned, publish_at, expires_at |
| `announcement_replies` / `announcement_reactions` | announcement_id, member_id, body / emoji_key |
| `notification_prefs` | member_id, category, push bool, email bool |
| `push_tokens` | member_id, expo_token, platform, last_seen_at |
| `calendar_feeds` | member_id, token_hash, revoked_at |
| `feedback` | member_id (nullable if anonymous), category, body, status |

## Platform
| Table | Key fields |
|---|---|
| `audit_log` 🔒 | actor_member_id, actor_role, action, table_name, row_id, before jsonb, after jsonb, ip_hash, at |
| `import_batches` | kind (members/finance/stats), file_name, file_sha256, status (uploaded/validated/committed/rolled_back), counts, committed_by |
| `import_rows` | batch_id, sheet, row_number, raw jsonb, normalised jsonb, status (ok/warning/error/duplicate), messages[] |

> ASSUMPTION: Field lists are a first draft. They'll be refined once the Excel workbook has been inspected (doc 11).
