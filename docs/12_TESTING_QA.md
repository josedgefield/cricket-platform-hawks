# 12 — Testing & QA

## Test pyramid
| Layer | Tool | Must cover |
|---|---|---|
| Unit (pure TS) | Vitest | Money formatting/rounding, allocation algorithm, balance maths, SGQR payload + CRC16, stats formulas with null rules, CSV/Excel parsers, bank matcher scoring |
| Property-based | fast-check | Ledger invariants: for any sequence of charges/payments/adjustments, balance = Σledger; allocations never exceed amounts |
| Database | pgTAP (via `supabase test db`) | **RLS for every role × table × CRUD**; append-only triggers; SQL functions (confirm claim, adjust, import commit/rollback); audit rows written |
| Integration | Vitest + local Supabase | Edge Functions: import-validate, bank-csv-match, notify outbox, ics-feed, (later) stripe-webhook with signed fixtures and replayed duplicate events |
| E2E web | Playwright | Admin: raise bulk charges → player claim → treasurer confirm → balance zero; Excel import preview → commit → rollback; role denial paths |
| E2E mobile | Maestro (or Detox) | Login (OTP stub), Home, availability toggle, Pay with PayNow sheet, claim submit |
| Visual/a11y | Storybook + axe; Playwright screenshots at 375/768/1440 | Contrast, focus order, touch target size, no horizontal scroll |

## Finance acceptance scenarios (must pass before Phase 1 exit)
1. A charge of S$120 is paid in two parts (S$50 + S$70), giving `paid`, balance 0.
2. An overpayment of S$150 against a S$120 charge leaves S$30 credit, which is auto-applied to the next charge.
3. A waiver of S$20 on a S$120 charge, then a payment of S$100, gives `paid`.
4. A wrong charge is voided: it's excluded from the balance and still visible in the statement.
5. A refund of a credit increases the balance correctly, with an audit row.
6. Uploading the same bank CSV twice is rejected. A duplicate line in a new file is flagged.
7. A treasurer without MFA cannot confirm a claim (RLS denies it, and the UI explains why).
8. A player cannot read another player's charges, claims or proofs (API + storage).
9. Rolling back an import batch reverses all its ledger effects and leaves the history intact.

## Stats acceptance
- A full season import reproduces the source totals (doc 06).
- Missing fields show "—" and are never computed as 0.

## Quality gates in CI
- 100% of RLS policies have tests (a script compares `pg_policies` with the test manifest).
- Coverage for `packages/domain` is ≥ 90% lines.
- No `any` in the domain or db packages; strict TypeScript.
- Lighthouse a11y score ≥ 95 on player web key pages.

## Manual QA per release
Smoke test on one iOS and one Android device, plus Chrome and Safari. Checklist: login, home, pay sheet, availability, notification receipt, offline banner, dark mode, largest text size.
