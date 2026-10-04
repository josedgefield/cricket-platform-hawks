---
name: end-session
description: Write the end-of-session progress report for the Hawks CC project as an Obsidian-ready Markdown file. Use when the user writes "End session" (any capitalisation, with or without punctuation), "end of session", "wrap up the session", "session report" or "progress report", or invokes /end-session.
---

# End-of-session progress report

Produce an honest progress report for this session, save it as Markdown that reads well in
Obsidian, keep the living feature list current, and commit both. Report what is true: say what
was not done, not verified, or drifted from the plan.

## 1. Gather the facts (read; don't rely on memory)

Run these from the repo root:

```bash
TZ=Asia/Singapore date +%F                 # report date (club's local date; servers run on UTC)
git rev-parse --abbrev-ref HEAD            # branch
git rev-parse --short HEAD                 # head commit
ls docs/progress/ 2>/dev/null | sort | tail -1   # previous report
```

- **Pull first:** `git fetch origin <branch>` and compare. The user also commits from their own
  machine; merge their commits in (fast-forward or merge, never rebase or force) before writing.
- **Since when:** take `head:` from the previous report's frontmatter and list commits since it:
  `git log --oneline <that-head>..HEAD`. With no previous report, use `git log --oneline main..HEAD`.
- **This conversation:** what the user asked for, what was built, what was only discussed or
  advised, what was decided, and what failed or was left unverified.
- **CI:** if GitHub tools are available, the latest run of each workflow on this branch
  (pass/fail and test counts if the log shows them). Otherwise write "not checked".
- **Pull requests:** open PRs for this branch, if GitHub tools are available.
- **The plan:** read `docs/13_ROADMAP_MVP.md` (phases and exit criteria), `docs/02_REQUIREMENTS.md`
  (requirement IDs and priorities), `docs/15_OPEN_QUESTIONS.md` (what the club owes) and
  `docs/FEATURES.md` (the living feature list).

## 2. Assess

- **Done this session:** each item tied to a feature row in `docs/FEATURES.md` or a requirement ID.
  Say what was verified and how (CI run, test count, local run), and what was not verified.
- **Against the plan:** for each phase, where it stands against its exit criterion in `docs/13`.
- **Drift check.** Be direct, not defensive:
  - work that maps to no requirement ID, or to a Could/Won't item;
  - work done out of roadmap order, and whether the reason holds (e.g. a blocked earlier phase);
  - topics revisited many times without a decision (name them);
  - scope changes (stack, priorities) and whether the docs were updated to match.
  End with one sentence of advice on what to stop, start or keep.
- **Next steps, in order:** at most 8, each one concrete. Blockers that only the club can resolve
  come first, as checklist items.

## 3. Update the living feature list

Edit `docs/FEATURES.md`: change the `Status` and `Waiting on` cells that changed this session, add
rows for new features in build order, and set `updated:` in its frontmatter to today. Never drop a
row; mark it `Dropped` with a reason instead.

## 4. Write the report

Copy `report-template.md` (next to this file) to `docs/progress/<YYYY-MM-DD>.md`. If that file
exists, use `<YYYY-MM-DD>-2.md`, `-3`, and so on. Fill every section; delete the HTML comments.

Obsidian conventions (the template already follows them):
- YAML frontmatter properties: `date`, `type`, `project`, `branch`, `head`, `previous`, `tags`.
- Callouts: `> [!summary]`, `> [!warning]`, `> [!todo]`.
- `- [ ]` tasks for anything someone must do; tables for status.
- Relative Markdown links (`../FEATURES.md`), which Obsidian and GitHub both resolve.
- No emoji, no HTML.

## 5. Deliver

1. **Obsidian copy (terminal only):** if the environment variable `HAWKS_OBSIDIAN_DIR` is set and
   the folder exists, also copy the report there:
   `cp docs/progress/<file>.md "$HAWKS_OBSIDIAN_DIR/"`.
2. **Commit** only the report and `docs/FEATURES.md`:
   `git add docs/progress/<file>.md docs/FEATURES.md && git commit -m "Progress report <date>"`
   (end the message with the session's attribution lines if the environment requires them).
3. **Push** to the current branch. In a cloud session this is required, or the report is lost
   when the container ends. In a local terminal, ask before pushing.
4. **Optionally publish:** if a Claude Docs connector is available and the user wants the report
   as a shareable doc, publish the same content there too. The Markdown file stays the record.

## 6. Reply

Three lines at most: the report's path, the one-sentence summary, and the top next step. Don't
repeat the report in chat.
