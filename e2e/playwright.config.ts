import { defineConfig, devices } from '@playwright/test';

/**
 * Runs against the real stack — bring it up first with `make e2e-up`
 * (or the full `make e2e` cycle, which also runs and tears it down).
 * See e2e/README.md.
 */
export default defineConfig({
  testDir: './specs',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  globalSetup: './global-setup.ts',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:81',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    // Pins a specific browser binary when the environment provides one that doesn't match this
    // project's @playwright/test version (see global-setup.ts, which honors the same variable
    // for its own manual browser launch). Unset in CI, where `playwright install` fetches the
    // matching revision instead.
    launchOptions: {
      executablePath: process.env.PLAYWRIGHT_CHROMIUM_PATH || undefined,
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    // Firefox/WebKit intentionally left out for now — one browser is enough
    // to catch the class of backend-integration bugs this suite targets
    // (see docs/specs/e2e-test-suite.md); add projects here if cross-browser
    // coverage becomes a priority.
  ],
});
