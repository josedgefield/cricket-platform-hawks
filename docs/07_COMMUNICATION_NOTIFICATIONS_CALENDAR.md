# 07 — Communication, Notifications, Calendar

## Announcements
- **Audiences:** all members, a team/squad, or the committee. Optional `publish_at` scheduling and `expires_at`.
- **Body:** markdown (sanitised), with optional attachments (PDF/images in a private bucket via signed URLs).
- Pinned announcements show at the top of Home.
- Engagement: **reactions** (a fixed set of 👍 ✅ 🏏 👏 ❓, shown as labelled reactions rather than icon-only) and **threaded replies**.
- Comms admins can see a **read count**. Who read what is not shown to other players.
- **Moderation:** the author or a comms admin can hide a reply with a reason, which is audited.

## Availability & selection
- Each fixture has availability options **Available / Maybe / Unavailable**, plus a note (e.g. "can only bat").
- Members are reminded at T-5 days and T-48h if they haven't responded.
- The captain sees a grid of availability by player with filters (role: batter, bowler, keeper).
- **Selection:** the captain picks the XI, plus 12th player and scorer. Publishing notifies the selected players and the squad.

## Notifications
| Channel | Tech | Use |
|---|---|---|
| Push | Expo Notifications → APNs/FCM | Time-sensitive: selection, match changes, claim confirmed, announcement |
| Email | Resend (transactional) | Receipts, statements, reminders, invites, OTP |
| In-app inbox | `notifications` table | History of everything sent to the member |

- **Preferences** are set per category (finance, matches, announcements, social). Finance receipts and security emails can't be disabled.
- **Privacy:** push text never includes amounts or other people's names. For example, *"Payment update — tap to view"*.
- **Reliability:** an outbox table is processed by an Edge Function, with retries and backoff. Bad push tokens are pruned from Expo receipts. Failures go to Sentry.
- **Quiet hours** are 22:00–07:00 SGT for non-urgent categories.

## Calendar
- Each member gets a **personal ICS feed**: `https://…/ics/<random-token>.ics`. Only the token hash is stored, and the member can revoke and regenerate it.
- It contains their team's fixtures (with venue address and map link), training sessions and finance due dates (optional).
- "Add to calendar" also works per fixture: a native calendar intent on mobile, a `.ics` download on web.
- **Travel planning:** each venue has a map link, parking and transport notes, and meeting time ("report 45 min before start").

## Feedback
- A simple form with category (app bug, club suggestion, other) and optional anonymity. It goes to the committee inbox in the admin console.
- Anonymous feedback stores no member id. Rate limiting uses a short-lived hashed token.

## WhatsApp coexistence
The app does not replace casual chat. Recommended rule: **"If it matters, it's in the app."** That covers fees, selections, fixture changes and official notices. WhatsApp stays for banter.

> ASSUMPTION: A WhatsApp Business API integration isn't needed in v1, because of its cost and complexity.
