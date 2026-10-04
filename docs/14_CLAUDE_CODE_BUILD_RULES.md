# 14 — Claude Code Build Rules

These rules apply to any AI-assisted (Claude Code) or human contribution to this repo.

## Before coding a feature
1. Read `CLAUDE.md`, `plan.md` and the relevant `docs/` files.
2. Use plan mode and state:
   - requirements (IDs from doc 02);
   - entities touched (doc 04);
   - permissions (doc 03);
   - migrations, API/functions, UI states, validation, error handling, tests and observability;
   - open assumptions.
3. Pick the **smallest coherent slice**. Finance and cricket-data features are never built UI-first.

## While coding
- **Migrations:** one SQL migration per change. Never edit an applied migration. Regenerate types.
- **Security:** authorisation rules + role tests in the **same PR** as any new endpoint or table.
- **Ledger:** never UPDATE or DELETE ledger rows; never add a mutable balance column.
- **Money:** integer cents; use `packages/domain/money.ts` helpers only.
- **Data:** never invent or default unknown cricket or finance data.
- **Secrets:** the service-role key never goes in client code. No secrets in code, tests or fixtures; use `.env.example` with placeholders.
- **PII:** no real member data in seeds, tests, screenshots or issues.
- **UI:** use tokens from `packages/ui` (generated from `design-system/hawks-cricket-club/MASTER.md`). Use the UI UX Pro Max skill for UI decisions and run its pre-delivery checklist. Page overrides go in `design-system/hawks-cricket-club/pages/`.
- **External calls:** timeouts, retries with backoff, idempotency, logging, a manual retry path, and "last synced" state.
- **Dependencies:** justify any new dependency in the PR description, and prefer what's already in the stack.

## Definition of done (from CLAUDE.md)
- [ ] Schema/migrations
- [ ] API/service layer (Spring module service + REST endpoint)
- [ ] Authorisation (Spring Security rules + role tests)
- [ ] Empty/loading/error/success (+ offline/stale) states
- [ ] Audit/observability for sensitive flows
- [ ] Automated tests (unit + db + e2e where relevant)
- [ ] Mobile + web behaviour considered
- [ ] Docs updated
- [ ] External failures have a safe UX

## Pull requests
- Small PRs with a clear title and description that links requirement IDs.
- CI must be green. Finance, authorisation and security changes need human review from a technical operator.
- No direct pushes to `main`.

## Vendored skills
- `.claude/skills/ui-ux-pro-max` is third-party (MIT) and pinned.
- To update: re-review the scripts (no network, subprocess or eval calls), update `VENDORED.md` with the new SHA, and open a dedicated PR.
