# Design System Master File — Hawks Cricket Club

> **LOGIC:** When building a specific page, first check `design-system/hawks-cricket-club/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file. If not, follow the rules below.

---

**Project:** Hawks Cricket Club (Hawks CC, Singapore)
**Generated with:** UI UX Pro Max skill (`.claude/skills/ui-ux-pro-max`, vendored), query `"sports club team community app" --design-system --variance 6 --motion 4 --density 5`, then adapted to the Hawks brand on 2026-10-04.
**Category:** Sports Team/Club (public site) + Financial Dashboard (finance screens) + Community app (comms)
**Design Dials:** Variance 6/10 (Balanced / Modern) · Motion 4/10 (Standard) · Density 5/10 (Standard; admin tables use density 8)

### What was kept vs. overridden from the generator

| Generator output | Decision | Reason |
|---|---|---|
| Palette: team red `#DC2626` + "championship gold" | **Overridden**: brand navy primary, gold kept as accent | The Hawks logo is navy on white. Gold was the generator's own secondary note and pairs at AAA with navy (7.03:1). |
| Typography: Bebas Neue + Source Sans 3 | **Overridden**: Montserrat (display) + Barlow (body) + Barlow Condensed (numerals/scoreboard) | The "HAWKS" wordmark is a wide geometric sans, so Montserrat matches it. The skill's "Sports/Fitness" pairing (Barlow / Barlow Condensed) handles body text and scoreboard-style figures. |
| Style: Minimalism | **Kept**: "Athletic Minimalism", a clean white/navy base with bold display type and a gold highlight used sparingly | It also fits the finance product guidance (Minimalism + Swiss, navy + gold accents for trust). |
| Pattern: Hero-Centric (public site) | **Kept** for the public website only | The player app uses a dashboard pattern instead (balance → next match → announcements). |
| Motion: Stagger list, `back.out` | **Kept** for marketing cards only | Per the generator's own rule, never on data tables or finance figures. |
| Spacing, shadows, checklist | **Kept** | |

---

## Brand

- **Logo:** `assets/brand/hawks-logo.jpg`. This is a raster; a vector SVG is requested (see `docs/15_OPEN_QUESTIONS.md`). Keep clear space of at least the height of the "H" around the logo. Never recolour it, except as all-white on navy.
- **Colour values are ESTIMATES sampled from the logo** until the club supplies official hex codes. Every value lives in tokens, so a change is a one-line edit.

## Global Rules

### Colour Palette — Light

| Role | Hex | CSS Variable | Contrast check |
|------|-----|--------------|----------------|
| Primary (Hawks Navy) | `#1E2A78` | `--color-primary` | white on navy 12.67:1 |
| Primary Strong | `#141C52` | `--color-primary-strong` | used for hero/nav backgrounds |
| On Primary | `#FFFFFF` | `--color-on-primary` | |
| Accent / CTA (Talon Gold) | `#F5B700` | `--color-accent` | navy on gold 7.03:1 |
| On Accent | `#141C52` | `--color-on-accent` | 8.85:1 |
| Accent Text (gold on white) | `#8A6200` | `--color-accent-text` | 5.49:1 |
| Background | `#F6F7FB` | `--color-background` | |
| Foreground | `#0F1533` | `--color-foreground` | 16.69:1 on bg |
| Card | `#FFFFFF` | `--color-card` | |
| Muted | `#EEF0F7` | `--color-muted` | |
| Muted Foreground | `#4A5272` | `--color-muted-foreground` | 7.16:1 on bg |
| Border (decorative) | `#D9DCEA` | `--color-border` | non-informational only |
| Input Border | `#6F7799` | `--color-input` | ≥3:1 non-text |
| Success | `#15803D` | `--color-success` | 5.02:1 |
| Warning | `#B45309` | `--color-warning` | 5.02:1 |
| Destructive / Overdue | `#B91C1C` | `--color-destructive` | 6.47:1 |
| Ring (focus) | `#F5B700` on navy, `#1E2A78` on light | `--color-ring` | |

### Colour Palette — Dark

| Role | Hex | Contrast check |
|------|-----|----------------|
| Background | `#0B1030` | |
| Card | `#141B45` | |
| Foreground | `#EEF0FA` | 16.34:1 |
| Muted Foreground | `#A9B0D0` | 7.71:1 on card |
| Primary (links/active) | `#9DACFF` | 7.68:1 on card |
| Accent | `#F5B700` | |
| Success / Warning / Danger | `#4ADE80` / `#FBBF24` / `#F87171` | 9.47 / 9.89 / 5.97 on card |

**Colour rules**
- Money states are never shown by colour alone. Every amount carries a label: **Owed**, **Paid**, **Pending confirmation** or **Overdue**, with an icon.
- Use gold sparingly: one primary CTA per view, the active tab indicator, and win/milestone highlights.

### Typography

- **Display / Headings:** Montserrat 700–800. Uppercase with +0.04em tracking for H1/section labels; title case for H2–H4.
- **Body / UI:** Barlow 400/500/600, base 16px, line-height 1.5.
- **Numerals / Scoreboard:** Barlow Condensed 600/700 with `font-variant-numeric: tabular-nums` for scores, stats and money.
- **Google Fonts:** `https://fonts.googleapis.com/css2?family=Montserrat:wght@600;700;800&family=Barlow:wght@400;500;600&family=Barlow+Condensed:wght@600;700&display=swap`
- **Native:** load the same families through `@expo-google-fonts/*`.

| Token | Size / line-height | Use |
|---|---|---|
| `display` | 48/52 (mobile 36/40) | Hero |
| `h1` | 32/40 | Page title |
| `h2` | 24/32 | Section |
| `h3` | 20/28 | Card title |
| `body` | 16/24 | Default |
| `small` | 14/20 | Meta (never below 12) |
| `stat-xl` | 40/44 Barlow Condensed | Balance, runs, wickets |

### Spacing Variables (Density 5)

| Token | Value | Usage |
|-------|-------|-------|
| `--space-xs` | `4px` | Tight gaps |
| `--space-sm` | `8px` | Icon gaps, inline spacing |
| `--space-md` | `16px` | Standard padding; mobile side gutter |
| `--space-lg` | `24px` | Section padding |
| `--space-xl` | `32px` | Large gaps |
| `--space-2xl` | `48px` | Section margins |
| `--space-3xl` | `64px` | Hero padding |

Admin/treasurer tables use a dense scale: 8/12/16 cell padding and 40px row height.

### Radius & Shadow

- Radius: `--radius-sm 6px` (chips), `--radius-md 10px` (inputs, buttons), `--radius-lg 16px` (cards), `--radius-full` (avatars, pills).
- Shadows: `--shadow-sm 0 1px 2px rgba(15,21,51,.06)`, `--shadow-md 0 4px 12px rgba(15,21,51,.08)`, `--shadow-lg 0 12px 24px rgba(15,21,51,.12)`.

### Brand motif

The logo's **inverted triangle** is used as a subtle motif: a chevron divider at the bottom of the hero, and a triangular "talon" notch on the active tab and the card accent stripe. Never use it as an icon with meaning.

---

## Component Specs

### Buttons
```css
.btn-primary   { background: var(--color-accent); color: var(--color-on-accent); padding: 12px 24px; min-height: 44px; border-radius: var(--radius-md); font: 700 15px/1 Montserrat; letter-spacing: .02em; transition: background-color 180ms ease, box-shadow 180ms ease; cursor: pointer; }
.btn-primary:hover { background: #FFC933; }
.btn-secondary { background: var(--color-primary); color: #fff; }               /* default action on light surfaces */
.btn-ghost     { background: transparent; color: var(--color-primary); border: 1.5px solid var(--color-primary); }
:focus-visible { outline: 3px solid var(--color-ring); outline-offset: 2px; }
```
- Hover states never shift layout (no translate on buttons inside lists or tables).
- Destructive actions (void charge, reverse payment) use `--color-destructive` with a confirmation dialog that requires a reason.

### Cards
```css
.card { background: var(--color-card); border-radius: var(--radius-lg); padding: 20px; box-shadow: var(--shadow-sm); border: 1px solid var(--color-border); }
.card--accent { border-top: 4px solid var(--color-accent); }
```

### Balance Card (finance hero)
- Navy background with a white `stat-xl` amount and a status chip (Owed / All paid up / Pending).
- Gold primary CTA: "Pay with PayNow".
- Secondary link: "View statement".

### Match Card
- Competition chip (SCA / BPL / IAT30), date and time (SGT), venue, and an opponent crest placeholder.
- Availability segmented control: **Available / Maybe / Unavailable**, each with an icon and text label and a 44px height.

### Inputs
```css
.input { padding: 12px 16px; border: 1.5px solid var(--color-input); border-radius: var(--radius-md); font-size: 16px; }
.input:focus-visible { border-color: var(--color-primary); box-shadow: 0 0 0 3px rgba(30,42,120,.2); }
```
- Labels are always visible, and errors appear inline below the field.
- Money inputs use `inputmode="decimal"`.

### Navigation
- **Player app (mobile):** a bottom tab bar with at most 5 items: Home, Matches, Stats, Money, More. It respects the safe area, and the active item gets a gold notch.
- **Web:** a sticky top nav on navy with a "Player login" CTA, and padding to offset the nav height.
- **Admin (desktop):** a left sidebar with Finance, Members, Matches, Stats, Imports, Announcements and Audit log.

### Tables (admin)
- A sticky header, numeric columns right-aligned with tabular numerals, and row status chips.
- A bulk-selection bar, filters persisted in the URL, and a CSV export.

### Charts (stats)
From the skill's `chart` domain:
- **Line chart:** runs or strike rate over a season.
- **Grouped bar:** player vs. player comparison. Use this rather than radar charts with more than 8 axes.
- **Bullet chart:** progress toward a season target.

Rules:
- Direct labels, plus line styles as well as colour.
- A visible data-table fallback.
- Library: Victory Native or Recharts (web).

---

## Motion

- Tokens: `--dur-fast 120ms` (press), `--dur-base 200ms` (hover/state), `--dur-slow 320ms` (sheet/modal).
- Easing: `cubic-bezier(.2,.8,.2,1)`.
- **Stagger List** (300–450ms, `back.out(1.4)`) is allowed only on public-site fixture and news cards.
- `prefers-reduced-motion: reduce` disables all non-essential motion.
- Never animate finance figures, and never "count up" a balance.

---

## Anti-Patterns (Do NOT Use)

- ❌ Static, stale content. Every data block shows **"Last updated / last synced"**.
- ❌ Emojis as icons. Use **Lucide** (web) / `lucide-react-native` with a 2px stroke, outline style.
- ❌ Colour-only status, especially for money and availability.
- ❌ Layout-shifting hovers, and number count-up animations on money.
- ❌ Placeholder-only labels; errors shown only at the top of a form.
- ❌ Hidden fees or ambiguous "balance". Always itemise charges.
- ❌ Raw hex in components. Use tokens only.

## Pre-Delivery Checklist

- [ ] No emojis as icons; consistent Lucide outline set
- [ ] `cursor: pointer` on clickable elements (web)
- [ ] Hover/press transitions of 120–200ms, with no layout shift
- [ ] Text contrast ≥ 4.5:1 in light **and** dark modes; non-text ≥ 3:1
- [ ] Visible focus ring (gold on navy, navy on light)
- [ ] `prefers-reduced-motion` respected
- [ ] Responsive at 375, 768, 1024 and 1440px; 16px side gutter on phones; no horizontal scroll
- [ ] Touch targets ≥ 44pt (iOS) / 48dp (Android); safe areas respected
- [ ] Money: tabular numerals, an SGD label, and a status shown with both text and icon
- [ ] Empty, loading, error and success states designed for every data view
