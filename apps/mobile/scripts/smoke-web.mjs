// Loads the exported web build in Chromium and checks the app renders and navigates.
// There's no backend in CI: API calls are answered by fakes below, so we can walk through
// sign-in, the tabs and the admin Users screen, and check error states don't crash.
// Usage: node scripts/smoke-web.mjs http://localhost:4173
import { chromium } from 'playwright';

const base = process.argv[2] ?? 'http://localhost:4173';
const api = 'http://localhost:8080';
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));

function check(cond, msg) {
  if (!cond) {
    console.error('FAIL:', msg);
    process.exitCode = 1;
  } else {
    console.log('ok:', msg);
  }
}

const visible = (text) => page.getByText(text).first().waitFor({ timeout: 15000 }).then(() => true, () => false);

// ---------- fake API ----------
const admin = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'captain@example.org',
  displayName: 'Casey Captain',
  phone: null,
  role: 'admin',
  status: 'active',
  playerId: null,
  invitedAt: '2026-10-01T00:00:00Z',
  inviteSentAt: '2026-10-01T00:00:00Z',
  inviteEmailError: null,
  lastSignInAt: '2026-10-08T00:00:00Z',
  manageable: false,
};
const invited = {
  ...admin,
  id: '00000000-0000-0000-0000-000000000002',
  email: 'new.player@example.org',
  displayName: 'Nina Newplayer',
  role: 'player',
  status: 'invited',
  lastSignInAt: null,
  inviteEmailError: 'Connection refused',
  manageable: true,
};
const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Authorization, Content-Type',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
};
const json = (route, status, body) =>
  route.fulfill({ status, headers: { ...cors, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

await page.route(`${api}/**`, (route) => {
  const req = route.request();
  const path = new URL(req.url()).pathname;
  if (req.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: cors });
  if (path === '/api/auth/sign-in') {
    const body = req.postDataJSON();
    return body.password === 'right password'
      ? json(route, 200, { token: 'fake-token', member: admin })
      : json(route, 401, { detail: 'Email or password is wrong.' });
  }
  if (path === '/api/auth/sign-out') return route.fulfill({ status: 204, headers: cors });
  if (path === '/api/me') return json(route, 200, admin);
  if (path === '/api/admin/members') return json(route, 200, [admin, invited]);
  if (path === `/api/admin/members/${invited.id}`) return json(route, 200, invited);
  if (path.startsWith('/api/auth/invitations/')) return json(route, 410, { detail: 'This invite link has expired.' });
  // Stats and anything else: the server is "down", so screens must show their error state.
  return json(route, 503, { detail: 'Service unavailable' });
});

// ---------- signed out ----------
await page.goto(base, { waitUntil: 'networkidle' });
check(await visible('Sign in'), 'signed-out visitors land on sign in');
check(await visible("There's no sign-up"), 'sign in explains invites');

await page.getByText('Forgot your password?').first().click();
check(await visible('Send reset link'), 'forgot password screen opens');
await page.getByText('Back to sign in').first().click();

await page.goto(`${base}/accept-invite?token=expired`, { waitUntil: 'networkidle' });
check(await visible('This invite link has expired.'), 'an expired invite shows the server message');

// ---------- sign in ----------
await page.goto(`${base}/sign-in`, { waitUntil: 'networkidle' });
await page.getByLabel('Email').fill('captain@example.org');
await page.getByLabel('Password').fill('wrong');
await page.getByRole('button', { name: 'Sign in' }).click();
check(await visible('Email or password is wrong.'), 'a wrong password shows the error');

await page.getByLabel('Password').fill('right password');
await page.getByRole('button', { name: 'Sign in' }).click();
check(await visible('Fly high'), 'signing in opens the home tab');

await page.getByRole('tab', { name: 'Stats' }).click();
check(await visible("Couldn't load this"), 'stats shows an error state, not a crash');
check(await page.getByRole('button', { name: 'Try again' }).first().isVisible(), 'stats offers a retry');

await page.getByRole('tab', { name: 'Money' }).click();
check(await visible('Not built yet'), 'money tab renders its placeholder');

// ---------- admin ----------
await page.getByRole('tab', { name: 'More' }).click();
check(await visible('Casey Captain'), 'more shows the signed-in member');
await page.getByText('Manage users').first().click();
check(await visible('Nina Newplayer'), 'users lists members');
check(await visible('Invite email failed'), 'users flags a failed invite email');
await page.getByText('Nina Newplayer').first().click();
check(await visible('Resend invite'), 'an invited player can be re-invited');

// A reload keeps the session (stored token).
await page.reload({ waitUntil: 'networkidle' });
check(await visible('Nina Newplayer'), 'the session survives a reload');

// ---------- sign out ----------
await page.goto(`${base}/more`, { waitUntil: 'networkidle' });
await page.getByRole('button', { name: 'Sign out' }).click();
check(await visible("There's no sign-up"), 'signing out returns to sign in');

check(errors.length === 0, `no uncaught page errors ${errors.length ? JSON.stringify(errors) : ''}`);
await page.screenshot({ path: 'smoke-web.png', fullPage: true });
await browser.close();
