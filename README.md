# cricket-platform-hawks

The web and mobile application (iOS, Android and web) for **Hawks Cricket Club**, Singapore. It covers player finances, match and stats data, and club communication.

> **Status:** Planning. No application code yet; see the implementation gates in [`plan.md`](plan.md#9-implementation-gates-from-claudemd).

## Start here
| | |
|---|---|
| [`plan.md`](plan.md) | Master plan: evaluation, database, payments and security recommendations, architecture, roadmap |
| [`CLAUDE.md`](CLAUDE.md) | Context and rules for AI-assisted development |
| [`docs/`](docs/00_README.md) | Detailed specifications (00–15 + sources) |
| [`design-system/hawks-cricket-club/MASTER.md`](design-system/hawks-cricket-club/MASTER.md) | Design system: tokens, components, checklist |
| [`design/prototype/`](design/prototype/index.html) | Clickable static prototype: website, combined SCA + CricHeroes stats page (`stats.html`), player app, treasurer console |
| [`assets/brand/`](assets/brand/) | Club logo |

## View the prototype locally
```bash
cd design/prototype && python3 -m http.server 8000
# open http://localhost:8000
```

## Design tooling
The [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) skill is vendored at `.claude/skills/ui-ux-pro-max`, so Claude Code sessions in this repo load it automatically:
```bash
python3 .claude/skills/ui-ux-pro-max/scripts/search.py "availability poll" --domain ux
```

## Never commit
Member spreadsheets, bank statements, `.env` files, or any real personal data. See `.gitignore` and `docs/10_SECURITY_PRIVACY_AUDIT.md`.
