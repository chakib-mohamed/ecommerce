import { expect, type Page } from '@playwright/test';
import type { TestUser } from './test-users';

/**
 * Drives the real login form (frontend/src/containers/Login/Login.tsx) to
 * authenticate as `user`. Waits for the redirect away from /login as proof
 * the login actually succeeded, not just that the form was submitted.
 */
export async function loginAs(page: Page, user: TestUser): Promise<void> {
  await page.goto('/login');
  await page.getByPlaceholder('you@email.com').fill(user.email);
  await page.getByPlaceholder('••••••••').fill(user.password);
  // Two "Log in" buttons exist on this page (the login/signup mode toggle and
  // the submit button) — target the submit button by type, not text, to avoid
  // ambiguity.
  await page.locator('button[type="submit"]').click();
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 10_000 });
}
