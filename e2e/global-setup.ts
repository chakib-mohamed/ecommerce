import { chromium, type FullConfig } from '@playwright/test';
import { loginAs } from './fixtures/auth';
import { ADMIN_USER, RETAIL_USER } from './fixtures/test-users';

/**
 * Logs in once per role via the real UI and saves each session's
 * storageState, so most specs can start already authenticated
 * (`test.use({ storageState: ... })`) instead of re-driving the login form
 * every test. auth.spec.ts is the one place that still drives login itself —
 * that's the point of it.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use?.baseURL ?? 'http://localhost:81';
  // Playwright doesn't apply a project's `launchOptions` to manually-created
  // browser instances outside the test fixtures, so honor the same env var an
  // environment might use to pin a specific browser binary.
  const executablePath = process.env.PLAYWRIGHT_CHROMIUM_PATH || undefined;
  const browser = await chromium.launch({ executablePath });

  for (const user of [RETAIL_USER, ADMIN_USER]) {
    const context = await browser.newContext({ baseURL });
    const page = await context.newPage();
    await loginAs(page, user);
    await context.storageState({ path: user.storageStatePath });
    await context.close();
  }

  await browser.close();
}
