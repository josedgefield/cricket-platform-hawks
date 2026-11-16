# 05 — Finance & Payments

## 1. Context
Hawks CC is currently an **informal team with no UEN**, as confirmed on 2026-10-04. Stripe Singapore requires a registered business, so **v1 uses no payment gateway**. Players pay by PayNow (or bank transfer or cash) and the treasurer confirms each payment.

The ledger is designed so that adding a gateway later changes only the *source* of payments, not the model.

## 2. Ledger rules (non-negotiable)
1. **The ledger is append-only.** `charges`, `payments`, `payment_allocations`, `adjustments` and `audit_log` reject UPDATE and DELETE at the database level.
2. **The balance is derived.** `balance = Σcharges − Σ(waivers + voids + credits) + Σrefunds − Σpayments`.
3. **Every correction is a new row** with `reason` and `approved_by`. A wrong charge is voided with an adjustment, never edited.
4. **Amounts are integer cents in SGD.** Display uses `S$1,234.50` with tabular numerals.
5. **Atomic operations happen in SQL functions:** `raise_charges(...)`, `confirm_payment_claim(...)`, `record_payment(...)`, `allocate_payment(...)`, `create_adjustment(...)`. Each writes its audit row in the same transaction.

## 2a. What is built (first slice, 2026-11-16)
The `finance` module in the backend (`V5__finance.sql`, `/api/admin/finance/*`, `/api/me/statement`) and the app's **Money** tab and **Club finances** admin screens.

- **Tables:** `charge_batches`, `charges`, `payments`, `payment_allocations`, `adjustments`. All are append-only by database trigger, and so is `audit_log`.
- **Rules live in Java** (`finance/domain/Ledger.java`, `FinanceService`), not SQL functions. This follows the stack decision in `docs/09`. Each change runs in one transaction with its audit row, with the member's row locked.
- **Raising charges:** an admin charges chosen members, or everyone who isn't deactivated. One action is one `charge_batch`.
- **Recording payments:** PayNow, bank, cash or other, entered by an admin after the money arrives.
- **Allocation:** after every change, unused money goes to the charge due first (charges with no due date last, then oldest). An admin can name a charge to pay first.
- **Taking an allocation back:** rows can't be changed, so cancelling a charge or a payment adds a "release" row for each allocation involved. The freed money is then applied again.
- **Adjustments,** each with a mandatory reason and the approving admin:

| Kind | Applies to | Effect |
|---|---|---|
| `void` | a charge | Cancels the whole charge (raised by mistake). Money already paid towards it moves to other charges |
| `waiver` | a charge | Forgives part or all of what is still unpaid |
| `credit` | a member | Counts like a payment with no money received; pays charges oldest first |
| `refund` | a payment | Records money sent back. Only the unused part of a payment can be refunded |
| `reversal` | a payment | Cancels the whole payment (recorded by mistake). The charges it paid reopen |

- **Balance** = charges − voids and waivers − payments + refunds and reversals − credits. Positive means the member owes.
- **Repeated requests:** every write carries a `requestKey`. Sending the same key again returns the first result, so a double tap never records a payment twice.
- **References:** `HWK-C-00001` for charges, `HWK-P-00001` for payments.

**Not built yet:** fee schedules (Q2), PayNow QR and "I've paid" claims (Q3), bank CSV matching, reminders, reports/CSV export, and 2FA for admins, which is required before real money data goes in.

## 3. Charge lifecycle
```
open ──(partial allocation)──► part_paid ──(fully allocated)──► paid
  │                                                   ▲
  ├──(waiver adjustment)──► waived                    │
  └──(void adjustment)────► void          refund adjustment re-opens credit
overdue = open/part_paid AND due_date < today (Asia/Singapore)
```

## 4. PayNow flow (v1)
1. The player opens **Money** and sees the outstanding total plus itemised open charges. They tap **Pay with PayNow**.
2. The app shows an **SGQR PayNow code**, built client-side by `packages/domain/paynow.ts` to the EMVCo merchant-presented QR spec. It contains:
   - the proxy type and value: the club or treasurer's PayNow mobile number, or a UEN later;
   - the **amount** (not editable);
   - the **reference**, either the oldest charge's reference or a payment reference `HWK-PAY-XXXXXX`;
   - an expiry date (optional).
3. The app also shows **Copy reference** and **Copy amount** buttons, plus bank-transfer details as a fallback.
4. The player pays in their banking app, returns, and taps **I've paid**. This creates a `payment_claim` (pending) with an optional screenshot (private bucket).
5. The treasurer gets a notification. In the **Claims** queue they choose one of:
   - **Confirm**: calls `confirm_payment_claim`, which creates a `payment` and allocations. The player is notified and gets an email receipt.
   - **Reject** with a reason, which notifies the player.
6. **Bank CSV matching (should-have):** the treasurer exports a statement CSV from their bank and uploads it.
   - A parser per bank format (DBS/POSB, OCBC, UOB) extracts date, amount, description and reference.
   - The matcher proposes `claim ↔ line` and `charge ↔ line` matches: an exact reference match scores highest, then amount + payer name + date window.
   - The treasurer approves matches in bulk. Unmatched lines stay in a queue.
   - Duplicate uploads are rejected by file hash; duplicate lines are rejected by line fingerprint.

**Security notes**
- The PayNow proxy number is club configuration, visible only to members.
- Show the **recipient name** on screen. Players should check that the name their bank displays matches before paying. This guards against a tampered QR.
- Screenshots are evidence only and never auto-confirm a payment.

> ASSUMPTION: PayNow QR generation for a personal mobile proxy with a fixed amount works in the major Singapore banking apps. This must be **tested with the treasurer's real bank app** before launch.

## 5. Other payment methods
- **Cash:** the treasurer records a `payment` with `method=cash` and a note. An optional "received by" field records who took the cash.
- **Bank transfer (FAST/GIRO):** same as above, or matched through the CSV upload.
- **Overpayment:** the unallocated payment amount becomes member credit and is auto-applied to the next charge (configurable).

## 6. Reminders & reports
- **Reminders:**
  - due in 7 days;
  - overdue at 1, 14 and 30 days;
  - at most 1 reminder per member per 5 days;
  - the treasurer can pause reminders for a member (hardship).
- **Reports (CSV and print view):**
  - outstanding by member (aged 0–30/31–60/61–90/90+);
  - collections by month and method;
  - charges by fee type;
  - a **monthly reconciliation pack** comparing the ledger total with the bank total, listing unmatched items, with a sign-off by a second committee member.

## 7. Stripe (Phase 5 — only after the club is registered)
| Concern | Design |
|---|---|
| Integration | `PaymentGateway` interface; `StripeGateway` uses Checkout Sessions with `paynow` + `card` |
| Authority | **Webhook is the source of truth**: `checkout.session.completed`, `payment_intent.succeeded`, `charge.refunded` |
| Idempotency | `gateway_events.event_id` unique; processing in a transaction; Idempotency-Key on all creates (`charge_id` + attempt) |
| Ledger | A webhook creates `payments(method='stripe', external_ref=pi_…)` + allocations; fees recorded as a separate expense line (not deducted from the member's payment) |
| Reconciliation | A nightly job pulls balance transactions and flags mismatches |
| Failure | Timeouts, retries with backoff, dead-letter table, manual "reprocess event" button, alert on failures |
| Security | Webhook signature verification; secret keys only in the server environment; PCI SAQ-A (hosted checkout) |
| Fees (indicative, verify) | PayNow ≈ 1.3%; domestic cards ≈ 3.4% + S$0.50 |

## 8. Recommendations to the committee
1. Use a **dedicated account** for club money, with PayNow registered on it.
2. **Two-person review** of the monthly reconciliation.
3. Publish a **fee and refund policy** in the app (in Money → Policy).
4. Plan to **register the club** (Registry of Societies) to get a UEN. Benefits: a club bank account, a cleaner tax and liability position, and Stripe eligibility.

> ASSUMPTION: Charges are per member. Family or group billing (one payer, several members) is not needed in v1.
