# Hawks CC app (Expo)

The player app for iOS, Android and the web, from one codebase (Expo SDK 57, Expo Router).
It talks to the Spring Boot backend in `../../backend` over its REST API.

Members sign in first (there's no sign-up: an admin invites them by email).

| Screen | Status |
|---|---|
| Sign in, forgot password, accept invite, reset password | **Live** |
| Home | Club server status, link to stats |
| Stats | **Live** player stats (batting, bowling, fielding) and the points table, filterable by source |
| More | **Live**: your details, change password, sign out; *Manage users* for admins and superusers |
| More → Manage users | **Live**: list/search, invite, edit, deactivate/reactivate, resend invite; superusers change roles |
| Matches, Money | Placeholders saying what's coming |

Locally, sign in as `dev-admin@hawks.local` / `dev-admin-password` (created by the backend's
`dev` profile). Invite emails go to Mailpit at http://localhost:8025; their links open this app
on http://localhost:8081.

## Prerequisites
- **Node.js 22 LTS** (https://nodejs.org). Check with `node -v`.
- **The backend running locally** (see `backend/README.md`): `.\gradlew.bat bootRun` on Windows, `./gradlew bootRun` on macOS/Linux.
- For a phone: the **Expo Go** app from the App Store or Play Store, with the phone on the same Wi-Fi as your computer.

## Run it
From the repo root:
```powershell
cd apps\mobile          # macOS/Linux: cd apps/mobile
npm install             # first time only (creates package-lock.json: please commit it)
npx expo start
```
Then, in the terminal Expo opens:

| Key | Opens |
|---|---|
| `w` | **Web**, at http://localhost:8081 in your browser. Easiest place to start |
| (scan the QR code) | **Phone**, with Expo Go (Android: in the app; iPhone: with the Camera app) |
| `a` | **Android emulator**, if Android Studio is installed |

### Pointing the app at the backend
By default the app calls `http://localhost:8080`, which works for **web on the same computer**.
For a phone or emulator, create `apps/mobile/.env.local` (copy `.env.example`):

| Where the app runs | `EXPO_PUBLIC_API_URL` |
|---|---|
| Web on this computer | `http://localhost:8080` |
| Android emulator | `http://10.0.2.2:8080` |
| Phone with Expo Go | `http://<your computer's Wi-Fi IP>:8080` (find it with `ipconfig` on Windows) |

Restart `npx expo start` after changing it. On Windows, allow Java through the firewall
for private networks the first time, or the phone can't reach port 8080.

The backend only accepts browser requests from `http://localhost:8081` in the `dev`
profile (CORS). Phones and emulators aren't affected by CORS.

## Checks
```powershell
npm run typecheck     # TypeScript
npm test              # unit tests for formatting rules (— for unknown, † for published rates)
npm run build:web     # production web build into dist/
```
CI (`.github/workflows/mobile.yml`) runs all three, confirms the dependencies match
Expo SDK 57, and opens the web build in a headless browser to check it renders and
navigates.

## Debugging
- **Web:** browser DevTools (F12). Network tab shows every API call.
- **Phone/emulator:** press `j` in the Expo terminal to open the JavaScript debugger.
- **"Can't reach the club server"** means the backend isn't running, the URL is wrong
  for where the app runs (see the table above), or a firewall is blocking port 8080.

## Structure
```
src/app/            screens (file-based routes): sign-in, accept-invite, forgot/reset-password,
                    (tabs)/ index, stats, matches, money, more, admin/users (list, [id])
src/components/     shared UI: Screen, Card, Field, Button, Notice, Segmented, loading/error/empty states
src/lib/auth.tsx    session: token in Keychain/Keystore (phones) or localStorage (web)
src/constants/      design tokens from design-system/hawks-cricket-club/MASTER.md
src/lib/api.ts      typed API client (10 s timeout, readable errors)
src/lib/format.ts   display rules, unit-tested in test/
```
