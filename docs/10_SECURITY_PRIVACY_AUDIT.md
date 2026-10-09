# 10 — Security, Privacy, Audit

## Assets & threats
| Asset | Main threats | Key controls |
|---|---|---|
| Finance ledger | Tampering, fraudulent confirmation, insider error | Append-only tables, SQL functions, MFA for treasurer, audit log, two-person monthly review |
| Member PII (names, emails, mobiles, photos, DOB?) | Leakage via API, over-broad access rules, lost phone | Default-deny authorisation + tests, minimal fields, secure storage, PDPA processes |
| Payment proofs (screenshots) | Exposure of bank details | Private bucket, signed URLs (≤ 5 min), EXIF strip, deletion after 12 months |
| Auth sessions | Phishing, token theft | OTP/magic link (no passwords), short JWT lifetime, refresh rotation, revoke-all sessions |
| Service-role key / DB creds | Leak in client bundle or repo | Server-only, secret stores, secret scanning + push protection, rotation runbook |
| PayNow QR / recipient | QR tampering, fake "treasurer" | Proxy only in club config (admin-changeable, audited, notifies all admins); recipient name shown |
| Imports (Excel/CSV) | Formula injection, malicious files, PII in repo | Parse server-side, treat cells as data, size/type limits, never committed |
| Exports (CSV) | CSV injection | Prefix cells beginning with `= + - @` with `'` |

## Controls checklist (baseline: OWASP ASVS Level 1, plus selected L2 items for finance)
**Authentication & sessions**
- [ ] Invite-only accounts; email OTP/magic link; Sign in with Apple (required on iOS when other social logins exist) + Google
- [ ] TOTP MFA enforced (`aal2`) for treasurer & club_admin routes
- [ ] Rate limits on OTP send/verify, claim submission, imports
- [ ] Session list + "sign out everywhere"; admin can revoke a member's sessions

**Authorisation**
- [ ] Default-deny Spring Security config; every endpoint declares its roles; app DB role cannot change schema
- [ ] Authorisation test per role × endpoint × operation
- [ ] Storage policies by `club_id/member_id/` prefix
- [ ] Two-person approval for granting treasurer/club_admin

**Data protection**
- [ ] TLS everywhere (managed); encryption at rest (OCI block volume encryption) and encrypted backups (age)
- [ ] No NRIC/FIN collected; DOB only if needed for juniors
- [ ] Mobile: `expo-secure-store`; no PII in AsyncStorage logs; optional biometric lock on Money
- [ ] Push payloads contain no PII/amounts
- [ ] Sentry PII scrubbing; logs never contain tokens, emails or amounts linked to names

**Web hardening (admin + player web)**
- [ ] CSP (no inline scripts except hashed), HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors 'none'`
- [ ] Server actions CSRF-safe; SameSite=Lax cookies
- [ ] Markdown sanitised (announcements)

**Supply chain & repo**
- [ ] Private repo; branch protection on `main` (PR review + green CI); no force-push
- [ ] Dependabot (weekly), CodeQL, dependency review, secret scanning + push protection
- [ ] GitHub Actions pinned by SHA; least-privilege `permissions:`
- [ ] Vendored third-party code (e.g. UI UX Pro Max) is pinned by commit, reviewed and recorded in `VENDORED.md`
- [ ] 2FA required on GitHub, Oracle Cloud, Expo, Apple and Google accounts

## Audit log
- Written by triggers on finance tables, `role_assignments`, `members`, `consents`, `import_batches` and club settings.
- Fields: actor, role, action, table, row, before/after, timestamp, hashed IP.
- **Append-only.** It's readable by club_admin (all) and treasurer (finance). No one can edit it through the app.
- Retention: 7 years for finance-related entries (to be confirmed by the committee); 2 years for others.

## PDPA (Personal Data Protection Act 2012, Singapore)
| Obligation | Implementation |
|---|---|
| Consent & purpose | Privacy notice at first login; purposes listed (membership admin, fees, cricket stats, comms); separate opt-ins for public squad photo/name and marketing |
| Notification | Privacy notice in app + website; changes versioned in `consents` |
| Access & correction | Self-service profile edit; "Request my data" export (JSON/CSV) handled by club_admin within 30 days |
| Accuracy | Members maintain own profile; stats aliases reviewed by stats admin |
| Protection | Controls above |
| Retention limitation | Retention schedule: inactive members' profile data anonymised after 2 years except finance records required for accounts |
| Transfer limitation | Primary data in Singapore region; sub-processors (Expo, Resend, Sentry, Vercel) listed in privacy notice |
| Accountability | Named **Data Protection Officer** (a committee member) with a contact email; this document + runbooks |
| Data breach notification | Breach runbook: assess within 30 days; if notifiable, **notify PDPC within 3 calendar days** and affected individuals where required |
| Minors | If under-18s are members: parental/guardian consent and restricted public exposure |

> ASSUMPTION: The club will appoint a DPO and approve the privacy notice before launch. This is legal guidance in summary only; the committee should seek proper advice if unsure.

## App store privacy
- Apple: privacy nutrition label, in-app **account deletion**, and Sign in with Apple.
- Google: Data safety form, and account deletion via both a web link and in-app.

## Incident response (summary)
1. Contain: revoke keys and sessions, disable the affected feature flag.
2. Assess: scope, data types, number of people affected.
3. Notify: committee, DPO, and the PDPC/individuals if notifiable.
4. Remediate and write a post-mortem in `docs/incidents/`.
