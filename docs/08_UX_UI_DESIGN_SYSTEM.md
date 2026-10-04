# 08 — UX/UI Design System

## Design tooling decision (CLAUDE.md first planning gate)
**Chosen: code-first** (club decision, 2026-10-04), supported by the **UI UX Pro Max** skill.

| Tool | Role | Where |
|---|---|---|
| UI UX Pro Max skill | Design intelligence: style, palette, typography, UX rules, chart guidance, pre-delivery checklist | `.claude/skills/ui-ux-pro-max/` (vendored, see `VENDORED.md`) |
| Design system master | Single source of truth for tokens and component rules | `design-system/hawks-cricket-club/MASTER.md` |
| Static prototype | Clickable HTML for stakeholder review before app code | `design/prototype/` |
| React Native Storybook (web build) | Component review during implementation | `apps/mobile/.storybook` (Phase 0) |
| Tokens package | Tailwind/NativeWind preset generated from MASTER.md | `packages/ui` (Phase 0) |

**Other design tools, if you want them later:**
- **Figma Community** (figma.com/community) has free Expo/React Native UI kits.
- The **Tokens Studio** plugin syncs tokens between Figma and code.
- **Mobbin** has reference patterns from real apps.

The code-first workflow doesn't rule these out. Tokens can be exported to Figma later.

### Using the skill
```bash
# regenerate / explore (stdlib Python 3, offline)
python3 .claude/skills/ui-ux-pro-max/scripts/search.py "sports club team community app" --design-system -p "Hawks Cricket Club"
python3 .claude/skills/ui-ux-pro-max/scripts/search.py "availability poll segmented control" --domain ux
python3 .claude/skills/ui-ux-pro-max/scripts/search.py "nativewind" --stack react-native
```
Page-specific overrides go in `design-system/hawks-cricket-club/pages/<page>.md`.

## Brand
- **Logo:** `assets/brand/hawks-logo.jpg`. A hawk head inside an inverted triangle, with the wordmark "HAWKS" in navy on white.
- **Colours:** navy `#1E2A78` (primary, **estimated**), deep navy `#141C52`, white, and **Talon Gold** `#F5B700` as the accent. Full light and dark tables, with contrast ratios, are in MASTER.md.
- **Type:** Montserrat for display, Barlow for body, Barlow Condensed for numerals.

> ASSUMPTION: The colour values were sampled from a JPEG and need official confirmation. A vector SVG logo has been requested.

## Information architecture
**Player app (bottom tabs, at most 5):**
1. **Home:** balance card, next match + availability, pinned announcements, latest result.
2. **Matches:** upcoming/past, availability, selection, scorecards.
3. **Stats:** my stats, season leaderboards, player profiles.
4. **Money:** balance, open charges, Pay with PayNow, claims, statement, policy.
5. **More:** announcements, calendar feed, profile, notification prefs, feedback, privacy, sign out.

**Admin console (desktop sidebar):** Dashboard, Finance (Charges, Claims, Payments, Bank import, Adjustments, Reports), Members, Matches, Stats (Imports, Mapping queue, Overrides), Announcements, Imports, Audit log, Settings.

**Public website:** Home (hero, next fixture, latest results, about, join, sponsors, contact), Fixtures & Results, Squad (consented members only), Join Us.

## Screen state requirements
Every data view must design all of these states:

| State | Pattern |
|---|---|
| Loading | Skeletons matching the layout (no spinners for > 300ms content) |
| Empty | Friendly message + next action (e.g. "No charges yet — you're all square") |
| Error | Plain-language message, retry button, support link; never a raw error |
| Offline (mobile) | Cached last-known data with an "Offline — showing data from 10:42" banner; finance actions disabled |
| Stale (stats) | "Last synced 9 days ago" badge |
| Success | Toast + updated data; finance confirmations also show the reference |

## Accessibility
- WCAG 2.2 AA, with every pair contrast-checked in MASTER.md.
- Touch targets ≥ 44pt/48dp.
- Supports Dynamic Type.
- Screen-reader labels on all icon buttons.
- Status is never conveyed by colour alone.
- Reduced motion is respected.

## Prototype
`design/prototype/` contains static HTML/CSS files with no build step. Open `index.html` to see:
- `index.html`: public website landing page
- `app-home.html`: player home (phone frame)
- `app-money.html`: Money tab + PayNow QR sheet
- `app-matches.html`: fixtures, availability and stats
- `admin-reconcile.html`: treasurer claims and bank-match console (desktop)

All data is fictional, and the QR codes are illustrative placeholders.
