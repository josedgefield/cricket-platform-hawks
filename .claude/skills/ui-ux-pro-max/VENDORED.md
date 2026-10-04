# Vendored third-party skill: UI UX Pro Max

| Field | Value |
|---|---|
| Upstream | https://github.com/nextlevelbuilder/ui-ux-pro-max-skill |
| Path copied | `.claude/skills/ui-ux-pro-max/` (upstream `scripts/tests/` omitted) |
| Commit | `477bcb28c9812b385cb51a4605ddf30d7b2266e2` |
| Plugin version | 2.13.0 |
| Licence | MIT (see `LICENSE`) |
| Vendored on | 2026-10-04 |
| Requires | Python 3 standard library only |

## Security review (2026-10-04)
- `scripts/*.py` import only the standard library: argparse, csv, json, re, pathlib, tempfile, hashlib and similar.
- There are **no** network calls (`urllib.request`, sockets, `requests`), no `subprocess` or `os.system`, and no `eval` or `exec`.
- The `urllib.parse` import in `validate_data.py` only parses URLs held in the data files.
- File writes happen only with `--persist`, into `design-system/<slug>/`. Slugs are sanitised, which prevents path traversal, and writes are atomic.

## Updating
1. Clone the upstream repo at the new tag or commit.
2. Re-run the review above: `grep -nE 'subprocess|os\.system|eval\(|exec\(|urllib\.request|socket|requests' scripts/*.py`.
3. Replace this folder, keeping `LICENSE` and this file. Update the commit SHA and date above.
4. Open a dedicated PR titled "chore: update ui-ux-pro-max skill to <sha>".

## Usage
See `docs/08_UX_UI_DESIGN_SYSTEM.md`. The project's design system lives at `design-system/hawks-cricket-club/MASTER.md`.
