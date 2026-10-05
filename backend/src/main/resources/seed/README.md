# Seed data

Public cricket stats bundled with the app and loaded on startup through the normal import
path (`StatsSeeder`). Re-loading unchanged files changes nothing. Values are exactly as the
source printed them; never edit a file to "fix" a figure.

| Folder / file | Source | Competition | Captured |
| --- | --- | --- | --- |
| `cricheroes/bpl-2025/bpl-2025-*.csv` | CricHeroes (PDF downloads, transcribed) | BPL 2025, Supreme group | 2026-10-04 |
| `sca/club-league-2025-div3/sca-div3-2025-*.csv` | SCA, scores.cricketsingapore.com (HAR capture) | SCA Club League 2025, Division 3 | 2026-10-05 |
| `player-links.csv` | Reviewed by hand | — | 2026-10-05 |

## Player links

The same person can be printed differently by each source ("Shreyas Puttur" on SCA, "Puttur
Shreyas" on CricHeroes). `player-links.csv` says which source names are one player, so the app
can add their figures together. Each row is `Player` (the name the app shows), `Source`
(`sca` or `cricheroes`) and `Source name` (as that source prints it, without role notes such as
"(hawks Club Admin)"). Names not listed stay as separate players.

Not linked yet, needs confirmation: BPL "Vishal" may be SCA "Vishal Doshi".
To add a link at runtime instead: `POST /api/admin/stats/player-links`.
