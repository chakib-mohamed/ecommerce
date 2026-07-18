import { expect, test } from '@playwright/test';
import { waitForApiCall } from '../fixtures/api';
import { RETAIL_USER } from '../fixtures/test-users';

/**
 * Login (frontend/src/containers/Login/Login.tsx -> POST /api/users/authenticate).
 *
 * This is the explicit regression test for the CORS bug found and fixed in
 * this branch's verification session: docker-compose.yml never set
 * CORS_ALLOWED_ORIGINS/CORS_ALLOW_CREDENTIALS for api-gateway, so
 * cors.allowed-origins defaulted to empty -> every POST/PUT/DELETE from a
 * real browser (which sends an Origin header, unlike curl) was
 * CORS-rejected with 403, regardless of path permissions. This was
 * invisible to GET-only / curl-based checks. Verified by reverting the
 * docker-compose.yml CORS env vars and confirming this spec (and in fact
 * global-setup.ts's own pre-authentication) fails red.
 *
 * A second change rode along in the same fix — explicitly permitAll()-ing
 * /api/users/authenticate rather than relying on the /api/users
 * (signup) matcher to cover it — but reverting *only* that change while
 * keeping CORS fixed did not reproduce a failure, so it isn't proven
 * necessary on its own; it's kept as explicit, harmless documentation of
 * intent rather than a load-bearing fix.
 *
 * Does NOT use a pre-authenticated storageState (unlike most other specs) —
 * this is the one place the full form-driven login flow must run for real.
 */
test.describe('Login', () => {
  test('logs in with seeded retail credentials and lands authenticated', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('you@email.com').fill(RETAIL_USER.email);
    await page.getByPlaceholder('••••••••').fill(RETAIL_USER.password);

    const [response] = await Promise.all([
      waitForApiCall(page, 'POST', /\/users\/authenticate$/),
      page.locator('button[type="submit"]').click(),
    ]);

    // The literal regression check: this must be 200, not 403.
    expect(response.status()).toBe(200);
    await expect(page).not.toHaveURL(/\/login$/);

    // Authenticated state is observable via /account rendering a greeting
    // instead of the "log in" prompt.
    await page.goto('/account');
    await expect(page.getByText(`Hello, ${RETAIL_USER.email.split('@')[0]}`)).toBeVisible();
  });

  test('rejects invalid credentials and stays unauthenticated', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('you@email.com').fill(RETAIL_USER.email);
    await page.getByPlaceholder('••••••••').fill('not-the-real-password');

    const [response] = await Promise.all([
      waitForApiCall(page, 'POST', /\/users\/authenticate$/),
      page.locator('button[type="submit"]').click(),
    ]);

    expect(response.status()).toBe(401);

    // Any 401 forces a full-page reload back to /login (axios-instance.ts's
    // response interceptor) — since we're already on /login, the URL doesn't
    // change, so waitForURL would resolve immediately without actually
    // waiting for that reload. Wait for the network to settle instead, which
    // spans the reload's own asset/bootstrap requests.
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveURL(/\/login$/);
    // A fresh, empty login form confirms the reload completed (rather than
    // navigating again, which can race with a still-settling reload).
    await expect(page.getByPlaceholder('you@email.com')).toHaveValue('');

    // Confirm no session was established off the back of the failed attempt.
    const accessToken = await page.evaluate(() => localStorage.getItem('access_token'));
    expect(accessToken).toBeNull();
  });
});
