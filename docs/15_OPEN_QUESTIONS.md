# 15 — Open Questions

Status key: ❓ open · ◐ partly answered · ✅ answered

## Answered (2026-10-04)
| # | Question | Answer |
|---|---|---|
| A1 | Do the docs/ files exist? | No. Claude drafted them from CLAUDE.md |
| A2 | Legal entity / UEN? | ✅ Informal team, so no Stripe in v1; PayNow QR + manual reconciliation |
| A3 | Repository | ✅ Use `josedgefield/cricket-platform-hawks` (private) |
| A4 | Design workflow | ✅ Code-first, plus the UI UX Pro Max skill |
| A5 | Budget | ✅ Under S$50/month |
| A6 | Users in first season | ✅ Fewer than 50 |
| A7 | Brand assets | ✅ Use estimates from the logo (navy + white, Montserrat-like) |

## Open — blocking a gate
| # | Question | Blocks | Why it matters |
|---|---|---|---|
| Q1 ❓ | Please share a **masked sample of the Excel workbook** (real headers, 5–10 rows per sheet) | Phase 1 import | Column mapping, opening balances |
| Q2 ❓ | **Fee structure:** season fee, match fees, nets, kit, tournament entry? Amounts, due dates, late fees, discounts (students)? | Phase 1 | Fee schedules + reminders |
| Q3 ❓ | **Who receives money?** Treasurer's personal PayNow, or a dedicated account? Which bank? | Phase 1 | PayNow proxy, bank CSV parser |
| Q4 ◐ | **SCA data:** the site is CricClubs (`scores.cricketsingapore.com`), which serves HTML pages only, with no JSON API (HAR, 2026-10-05). Its CSV/Excel buttons export the on-page table, and scorecards and results pages are public. **Still open:** do CricClubs/SCA terms allow scheduled fetching of these pages, or should we ask SCA for a data feed? Until then, a stats admin imports after each match day. | Phase 3 automation | Manual vs scheduled SCA sync |
| Q23 ❓ | **Player links:** is BPL "Vishal" the same person as SCA "Vishal Doshi"? Not linked until confirmed (`seed/player-links.csv`). Please also confirm the other 11 links in that file. | Phase 3 | Combined totals |
| Q5 ◐ | **BPL and IAT30:** who runs them, and where do fixtures and stats live? **BPL is on CricHeroes** (tournament 1500354, Hawks team 10178708). IAT30 is still unknown: is it on CricHeroes too? | Phase 3 | Adapter design |
| Q19 ❓ | **CricHeroes access:** will CricHeroes or the BPL organiser provide an authorised export or API? Their site blocks automated requests (Cloudflare 403). Until then, a stats admin pastes or uploads leaderboard tables by hand. | Phase 3 automation | Decides manual vs automated sync for BPL |
| Q21 ❓ | **Does the CricHeroes team leaderboard cover only BPL 2025,** or every Hawks match on CricHeroes (e.g. friendlies or other tournaments)? The site currently labels it "BPL 2025". | Phase 3 | Competition attribution |
| Q22 ❓ | **Public website consent:** stats pages show players' full CricHeroes names. Confirm this is OK for the public site, or use the planned per-player name opt-in (`docs/10`). | Public launch | PDPA |
| Q20 ❓ | **Player name mapping:** please share each player's CricHeroes display name (from the members page), so they can be mapped to members | Phase 3 | `player_aliases` seed |
| Q6 ❓ | **Role holders:** treasurer, captain(s), stats admin, comms admin, club admins (≥ 2 for the two-person rule) | Phase 0 | Seeding roles |

## Open — non-blocking
| # | Question | Default if unanswered |
|---|---|---|
| Q7 ❓ | Any members **under 18**? | Assume no; add guardian consent if yes |
| Q8 ❓ | Can captains see squad members' outstanding balances? | No (treasurer + admins only) |
| Q9 ❓ | Refund / waiver / hardship policy? | Treasurer discretion, reason mandatory |
| Q10 ❓ | How many teams/squads (SCA divisions)? | One squad, many competitions |
| Q11 ❓ | What stays on WhatsApp? | Banter only; "if it matters, it's in the app" |
| Q12 ❓ | Official brand hex values, font licence, **SVG logo**? | Estimated tokens |
| Q13 ❓ | Who is the Data Protection Officer contact? | A committee member to be named before launch |
| Q14 ❓ | Plan to register as a society (UEN) in the next 12 months? | Stripe stays in Phase 5 |
| Q15 ❓ | Public website domain (e.g. hawkscc.sg)? Existing site/socials? | Use a subdomain of the hosting provider until decided |
| Q16 ❓ | Who will own the Apple/Google developer accounts? Ideally a club-controlled email | Club secretary's club email |
| Q17 ❓ | Sponsors to feature on the website? | Placeholder section |
| Q18 ❓ | Finance record retention period (7 years proposed)? | 7 years |
