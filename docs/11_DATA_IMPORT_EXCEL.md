# 11 — Data Import (Excel)

The current club workbook is an **input**, not the long-term source of truth (CLAUDE.md §10).

> ASSUMPTION: The workbook hasn't been seen yet. Its sheets probably include members/contacts, fees and payments, and possibly match or stat records. This doc defines the process; column mappings will be written after inspecting a **masked sample**.

## Process
```
Upload (.xlsx/.csv, ≤10 MB)
   → file hash check (already imported? → stop)
   → parse each sheet server-side (SheetJS, values only, formulas ignored)
   → map columns (saved mapping profile per sheet)
   → validate + normalise each row → import_rows (ok / warning / error / duplicate)
   → PREVIEW: counts, row-level messages, diff vs existing data
   → Commit (SQL function, one transaction, all rows tagged import_batch_id)
   → Rollback available: reverses by batch (members archived, ledger rows reversed with adjustments)
```

## Rules
| Rule | Detail |
|---|---|
| Explicit | Nothing is written to live tables until **Commit** is clicked by an authorised role |
| Previewable | Preview shows new vs matched members, charges/payments to be created, and every warning |
| Validated | Required fields, email format, SG mobile format (+65 8/9xxxxxxx), amounts ≥ 0 with 2 dp, dates parse unambiguously (DD/MM/YYYY assumed — **confirm**) |
| Idempotent | Row fingerprint (sheet + normalised key fields) means re-importing the same row is a no-op |
| Reversible | Rollback by batch; finance rollback uses reversal adjustments so the ledger stays append-only |
| Unknowns preserved | Blank cells stay NULL; no defaults invented (e.g. a missing payment date is not set to "today") |
| Opening balances | If the workbook only has totals, import an **opening balance charge/credit** per member dated at cut-over, with reason "Opening balance from Excel as at <date>" |
| PII handling | The uploaded file is deleted after parsing; `import_rows.raw` is purged 90 days after commit; the workbook is **never committed to git** |

## Member matching
1. Exact email match.
2. Then normalised mobile.
3. Then a fuzzy name match, shown as a warning only, which the user must confirm in the preview.

## Cut-over plan
1. Freeze the Excel workbook at date X.
2. Import members.
3. Import opening balances.
4. Treasurer and a second committee member verify per-member totals against the Excel file. They sign off in the app (recorded in the audit log).
5. From date X onward, all finance happens in the app only.

## Needed from the club
- A masked sample with real column headers and 5–10 representative rows per sheet, including edge cases (part payments, waivers, refunds, cash).
- Confirmation of the date format and currency (all SGD?).
