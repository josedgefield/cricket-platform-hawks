# 01 — Product Vision

## Problem
Hawks CC runs on WhatsApp groups and an Excel workbook. Three things hurt:
1. **Money.** Players don't know exactly what they owe. The treasurer chases payments by hand and reconciles bank transfers against a spreadsheet.
2. **Cricket data.** Matches and stats are spread across the SCA Club League site and tournament sources (BPL, IAT30), each with different formats.
3. **Communication.** Announcements get buried in chat, availability polls are messy, and there's no shared calendar.

## Vision
> *One Hawks app where every player can see their dues, their matches and their stats, and never miss a club notice.*

## Users
| Persona | Main device | Core jobs |
|---|---|---|
| **Player** | Phone | Check balance and pay; mark availability; see fixtures, results and own stats; read notices |
| **Captain / vice-captain** | Phone + laptop | See availability, pick the XI, post match notices |
| **Treasurer** | Laptop | Raise charges, confirm payments, reconcile, chase, report |
| **Stats admin** | Laptop | Import/verify match data, fix mappings |
| **Club admin / committee** | Laptop | Members, roles, announcements, settings, audit |
| **Public visitor** | Phone/laptop | Learn about the club, fixtures/results, how to join, sponsors |

## Success metrics (first season)
- ≥ 90% of active players have logged in within 4 weeks of launch.
- Treasurer reconciliation time down ≥ 50% compared with the Excel process (measured by self-report).
- Outstanding balances older than 60 days down ≥ 50%.
- ≥ 80% of fixtures have availability responses from ≥ 80% of the squad 48h before the match.
- Zero finance discrepancies between the app ledger and the bank at monthly reconciliation.

## Principles
- **Trust before features.** Finance numbers must always be explainable line by line.
- **Phone first for players, desk first for admins.**
- **Never invent data.** "Unknown" is a valid value.
- **Single club now, multi-club later.** Records are tenant-aware, but we won't build a SaaS.

## Non-goals (v1)
Chat/DMs, live scoring, in-app card payments, merchandise shop, multi-club onboarding.

> ASSUMPTION: The success metrics above are proposed targets and the club hasn't agreed them yet.
