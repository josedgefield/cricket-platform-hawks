# CricHeroes · BPL 2025 (seed data)

Transcribed by hand from CricHeroes PDF downloads supplied by the club on 2026-10-04.
The PDFs are images (no text layer), so these values were typed in exactly as printed:

| File | Source PDF | Printed columns |
|---|---|---|
| `bpl-2025-batting.csv` | hawks-cc-batting-leaderboard.pdf (top 10) | Inn, Runs, Avg, SR |
| `bpl-2025-bowling.csv` | hawks-cc-bowling-leaderboard.pdf (top 10) | Inn, W, Eco, Avg |
| `bpl-2025-fielding.csv` | hawks-cc-fielding-leaderboard.pdf (top 10) | Mat, Dismissal, Catches, R/O |
| `bpl-2025-points-table-supreme.csv` | points_table_BPL_2025.pdf, Supreme group | as printed; team names in the source's capitals |

CricHeroes doesn't print balls faced, not-outs, runs conceded or balls bowled. At import, a
missing count is filled only when exactly one whole number reproduces the published rate (e.g.
149 runs at SR 131.86 can only be 113 balls); such values are marked "recovered" in the API.
When more than one value fits (an average printed as "12"), the count stays unknown.

Do not "fix" or fill in values here. Apart from exact recovery, anything not printed stays unknown. Corrections
belong in a new import (the old raw record is kept) or, later, a stats override with a reason.
