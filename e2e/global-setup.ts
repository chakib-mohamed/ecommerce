import { chromium, request as playwrightRequest, type FullConfig } from '@playwright/test';
import fs from 'node:fs';
import { loginAs } from './fixtures/auth';
import { waitUntilTheStackCanSell } from './fixtures/smoke';
import {
  ADMIN_USER,
  RETAIL_USER,
  SPEC_BUYERS,
  generateSpecUsers,
  writeSpecUserRegistry,
  type TestUser,
} from './fixtures/test-users';

/** Registers an account through the real sign-up endpoint. */
async function register(baseURL: string, user: TestUser): Promise<void> {
  const api = await playwrightRequest.newContext({ baseURL });
  try {
    const res = await api.post('/api/users', {
      data: { email: user.email, password: user.password },
    });
    if (!res.ok()) {
      throw new Error(
        `Could not register ${user.email} (${res.status()}): ${(await res.text()).slice(0, 200)}`,
      );
    }
  } finally {
    await api.dispose();
  }
}

/**
 * Prepares every session the suite runs as, then blocks until the stack can actually sell.
 *
 * <p>Two seeded accounts are logged in as-is: auth.spec needs a fixed credential pair to drive the
 * real login form with, and admin.spec needs the admin role.
 *
 * <p>Everything that places an order gets its own freshly registered buyer instead — an order
 * history is mutable shared state, and these specs run in parallel. See fixtures/test-users.ts for
 * what sharing one buyer actually cost.
 *
 * <p>Sessions are established by driving the real login form rather than by writing a token into
 * storage directly. It is slower, and it is the same path a user takes, so a session saved here
 * cannot be more capable than one a person could get.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use?.baseURL ?? 'http://localhost:81';
  // Playwright doesn't apply a project's `launchOptions` to manually-created
  // browser instances outside the test fixtures, so honor the same env var an
  // environment might use to pin a specific browser binary.
  const executablePath = process.env.PLAYWRIGHT_CHROMIUM_PATH || undefined;

  fs.mkdirSync('.auth', { recursive: true });

  // Distinct per run, so a stack left up between runs never hands this run the previous one's
  // orders. The suffix is short and readable rather than a UUID - these show up in logs.
  const runId = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
  const specUsers = generateSpecUsers(runId);

  // Registration is independent per account and goes straight to the API, so it is done together.
  await Promise.all(SPEC_BUYERS.map((slug) => register(baseURL, specUsers[slug])));
  // Written only once every account exists, so a spec can never read an address that failed to
  // register and get a confusing 401 instead of the real error.
  writeSpecUserRegistry(specUsers);

  const browser = await chromium.launch({ executablePath });
  try {
    for (const user of [RETAIL_USER, ADMIN_USER, ...SPEC_BUYERS.map((s) => specUsers[s])]) {
      const context = await browser.newContext({ baseURL });
      const page = await context.newPage();
      await loginAs(page, user);
      await context.storageState({ path: user.storageStatePath });
      await context.close();
    }
  } finally {
    await browser.close();
  }

  // Last, and with its own buyer so the order it places lands in nobody else's history — see
  // fixtures/smoke.ts for why "healthy" is not the same as "ready".
  await waitUntilTheStackCanSell(baseURL, specUsers.smoke);
}
