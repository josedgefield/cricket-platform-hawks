---
name: end-session
description: Write the end-of-session progress report for the Hawks CC project as an Obsidian-ready Markdown file in the user's Obsidian vault (not in the repo). Use when the user writes "End session" (any capitalisation, with or without punctuation), "end of session", "wrap up the session", "session report" or "progress report", or invokes /end-session.
---

# End-of-session progress report

Produce an honest progress report for this session as Markdown for the user's Obsidian vault, and
keep the living feature list current. Report what is true: say what was not done, not verified,
or drifted from the plan.

**Reports never go in this repo.** They belong in the user's vault. The repo keeps only
`docs/FEATURES.md`, whose frontmatter records when the last report was written.

## Where the report goes

| Session | Destination |
| --- | --- |
| Claude Code on the user's PC | Write the file directly into the vault folder (below) |
| Cloud session (no access to the user's PC) | Write it to the scratchpad (or a temp folder) and hand it to the user as a downloadable file (`SendUserFile`, `display: "attach"`), saying which vault folder to save it in |

**Vault folder:** the environment variable `HAWKS_OBSIDIAN_DIR` if set; otherwise
`C:\Users\shrey\Documents\Obsidian\Hawks Cricket App\Progress Reports`
(Git Bash / WSL: `/c/Users/shrey/Documents/Obsidian/Hawks Cricket App/Progress Reports`).
Create the `Progress Reports` folder if the vault exists but the folder doesn't. If neither the
variable nor the default path exists on this machine, treat it as a cloud session.

**File name:** `<YYYY-MM-DD> Hawks progress.md`; if it exists, `<YYYY-MM-DD> Hawks progress 2.md`, and so on.

## 1. Gather the facts (read; don't rely on memory)

```bash
git fetch origin "$(git rev-parse --abbrev-ref HEAD)"   # the user also commits from their PC
TZ=Asia/Singapore date +%F                 # report date (club's local date; servers run on UTC)
git rev-parse --abbrev-ref HEAD            # branch
git rev-parse --short HEAD                 # head commit
```

- **Pull first:** if the remote branch is ahead, merge it in (fast-forward or merge; never rebase
  or force) before writing anything.
- **Since when:** `last_report_head` in `docs/FEATURES.md`'s frontmatter is where the previous
  report ended: `git log --oneline <last_report_head>..HEAD`. If it's missing, use
  `git log --oneline main..HEAD`.
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
rows for new features in build order, and in its frontmatter set `updated:` to today,
`last_report:` to today and `last_report_head:` to the head commit from step 1. Never drop a row;
mark it `Dropped` with a reason instead.

## 4. Write the report

Fill `report-template.md` (next to this file) and save it to the destination above. Fill every
section; delete the HTML comments.

The report must stand on its own in Obsidian, where repo files aren't available:
- copy the feature-status counts and every row whose status changed into the report;
- link repo files with full GitHub URLs on the current branch
  (`https://github.com/josedgefield/cricket-platform-hawks/blob/<branch>/docs/FEATURES.md`);
- link the previous report by its note name, `[[<YYYY-MM-DD> Hawks progress]]`.

Obsidian conventions (the template follows them): YAML frontmatter properties, callouts
(`> [!summary]`, `> [!warning]`, `> [!todo]`), `- [ ]` tasks, tables, no emoji, no HTML.

## 5. Deliver

1. **Save the report** to the vault, or send it as a file in a cloud session (see the table above).
2. **Commit only `docs/FEATURES.md`** (`git add docs/FEATURES.md && git commit -m "Update feature
   list after session <date>"`, plus the environment's attribution lines if it requires them).
   Never add the report to the repo.
3. **Push** in a cloud session (otherwise the commit is lost when the container ends); in a local
   terminal, ask before pushing.

## 6. Reply

Three lines at most: where the report is (or "download it and save it to <vault folder>"), the
one-sentence summary, and the top next step. Don't repeat the report in chat.
