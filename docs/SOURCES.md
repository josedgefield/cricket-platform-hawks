# Sources

External references used in planning. Figures marked *verify* must be checked again at the time of implementation, because pricing and policies change.

| Topic | Source | Notes |
|---|---|---|
| Project context | `CLAUDE.md` (provided by the club) | Mission, principles, recommended stack |
| Brand | `assets/brand/hawks-logo.jpg` (provided by the club) | Colours/fonts estimated from this image |
| UI/UX design intelligence | [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) (MIT), commit `477bcb28c9812b385cb51a4605ddf30d7b2266e2` | Vendored at `.claude/skills/ui-ux-pro-max` |
| Stripe Singapore fees | [WorldFirst — Stripe fees Singapore](https://www.worldfirst.com/sg/blog/international-transactions/stripe-fees-singapore/) | PayNow ~1.3%, no fixed fee; Stripe available to Singapore-registered businesses — *verify on stripe.com/en-sg/pricing* |
| Stripe Singapore overview | [WorldFirst — Stripe Singapore payment guide](https://www.worldfirst.com/sg/blog/international-transactions/stripe-singapore-payment-guide/) | |
| Singapore payment gateways | [OneCart — Payment gateways Singapore 2026](https://www.getonecart.com/list-of-payment-gateways-in-singapore/) | Comparison context |
| PayNow / SGQR | MAS / EMVCo Merchant-Presented QR specification (SGQR) | QR payload format — *verify against latest SGQR spec* |
| PDPA | Personal Data Protection Commission (pdpc.gov.sg) — PDPA obligations and data breach notification guidance | 3-calendar-day notification to PDPC once a breach is assessed as notifiable |
| Apple payments rule | App Store Review Guidelines §3.1.3(e) "Goods and Services Outside of the App" | Real-world services may use external payment — *verify before submission* |
| Apple sign-in & deletion | App Store Review Guidelines §4.8 (Sign in with Apple), §5.1.1(v) (account deletion) | *verify* |
| Supabase | supabase.com/docs, supabase.com/pricing | Singapore region `ap-southeast-1`; Pro plan backups; free projects pause when inactive — *verify* |
| Expo | docs.expo.dev (Router, Notifications, EAS) | |
| OWASP ASVS | owasp.org/www-project-application-security-verification-standard | Security baseline |
| WCAG 2.2 | w3.org/TR/WCAG22 | Accessibility baseline |
| CricHeroes: Hawks CC | [members](https://cricheroes.com/team-profile/10178708/hawks-cc/members), [matches](https://cricheroes.com/team-profile/10178708/hawks-cc/matches), [leaderboard](https://cricheroes.com/team-profile/10178708/hawks-cc/leaderboard) | Provided by the club. Returned Cloudflare 403 to automated requests on 2026-10-04 |
| CricHeroes: BPL 2025 | [past matches](https://cricheroes.com/tournament/1500354/bpl-2025/matches/past-matches), [points table](https://cricheroes.com/tournament/1500354/bpl-2025/point-table) | Provided by the club. Same 403. See `docs/06` |
