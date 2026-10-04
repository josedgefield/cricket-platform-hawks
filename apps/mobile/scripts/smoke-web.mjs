// Loads the exported web build in Chromium and checks the app renders and navigates.
// Runs without a backend, so the Stats screen must show its error state, not crash.
// Usage: node scripts/smoke-web.mjs http://localhost:4173
import { chromium } from 'playwright';

const base = process.argv[2] ?? 'http://localhost:4173';
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

await page.goto(base, { waitUntil: 'networkidle' });
check(await page.getByText('Hawks CC').first().isVisible(), 'home screen renders');
check(await page.getByText('Offline').first().isVisible().catch(() => false), 'home shows offline state without a server');

await page.getByRole('tab', { name: 'Stats' }).click();
await page.getByText("Couldn't load this").first().waitFor({ timeout: 15000 }).catch(() => {});
check(await page.getByText("Couldn't load this").first().isVisible(), 'stats shows an error state, not a crash');
check(await page.getByRole('button', { name: 'Try again' }).first().isVisible(), 'stats offers a retry');

await page.getByRole('tab', { name: 'Money' }).click();
check(await page.getByText('Not built yet').first().isVisible(), 'money tab renders its placeholder');

check(errors.length === 0, `no uncaught page errors ${errors.length ? JSON.stringify(errors) : ''}`);
await page.screenshot({ path: 'smoke-web.png', fullPage: true });
await browser.close();
