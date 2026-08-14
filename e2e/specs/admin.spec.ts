import { expect, test } from '@playwright/test';
import { waitForApiCall } from '../fixtures/api';
import { ADMIN_USER } from '../fixtures/test-users';

test.use({ storageState: ADMIN_USER.storageStatePath });

/**
 * Admin back-office (frontend/src/containers/Admin/*). Products/categories
 * render real data; the categories spec also performs one real write (create
 * + delete a throwaway subcategory) to prove add/edit/delete actually work,
 * cleaning up in the same test so no state leaks into shared seed data.
 */
/**
 * How long the first assertion on a back-office page may wait.
 *
 * <p>These pages render before their data arrives and again once it has, and the controls this
 * spec drives exist only in the second render. On the categories page that is explicit: the
 * accordion starts with nothing expanded, and an effect opens the first category once the catalog
 * has loaded, so `+ Add subcategory` does not exist until then - `setCats` and `setOpen` are in the
 * same effect, so no earlier element resolves any sooner. That whole sequence, catalog fetch
 * included, has to fit in the first assertion's budget, and Playwright's 5s default is a tight one
 * for it while the rest of the suite is loading the same stack in parallel.
 *
 * <p>Only the first assertion on each page gets this. Everything after it is interaction against a
 * page that has already proved it has data, so a slow response there is still a failure.
 */
const FIRST_RENDER_TIMEOUT_MS = 20_000;

test.describe('Admin back-office', () => {
  test('/admin/products renders products with live stock/price and edit/delete controls', async ({
    page,
  }) => {
    await page.goto('/admin/products');
    await expect(page.getByRole('columnheader', { name: 'Price' }).first())
      .toBeVisible({ timeout: FIRST_RENDER_TIMEOUT_MS });
    await expect(page.getByRole('columnheader', { name: 'Stock' }).first()).toBeVisible();
    expect(await page.getByRole('button', { name: 'Edit' }).count()).toBeGreaterThan(0);
    expect(await page.getByRole('button', { name: 'Delete' }).count()).toBeGreaterThan(0);
  });

  test('/admin/categories renders the hierarchy and supports adding + deleting a subcategory', async ({
    page,
  }) => {
    await page.goto('/admin/categories');

    // The hierarchy, which is the first half of what this test's name claims and was never
    // actually asserted. It also separates the two ways this can fail: no categories at all reads
    // differently from categories that rendered without their controls.
    await expect(page.getByRole('button', { name: 'Edit' }).first())
      .toBeVisible({ timeout: FIRST_RENDER_TIMEOUT_MS });

    const addButton = page.getByRole('button', { name: '+ Add subcategory' }).first();
    await expect(addButton).toBeVisible();

    const subcategoryName = `E2E Temp ${Date.now()}`;
    await addButton.click();
    await page.getByPlaceholder('Subcategory name').fill(subcategoryName);

    const [createResponse] = await Promise.all([
      waitForApiCall(page, 'POST', /\/categories$/),
      page.getByRole('button', { name: 'Add' }).click(),
    ]);
    expect(createResponse.ok()).toBe(true);
    await expect(page.getByText(subcategoryName, { exact: true })).toBeVisible();

    // Cleanup: delete the throwaway subcategory so it doesn't leak into shared seed data.
    const row = page.getByText(subcategoryName, { exact: true }).locator('..');
    await row.getByRole('button', { name: 'Delete' }).click();

    const [deleteResponse] = await Promise.all([
      waitForApiCall(page, 'DELETE', /\/categories\/.+/),
      page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click(),
    ]);
    expect(deleteResponse.ok()).toBe(true);
    await expect(page.getByText(subcategoryName, { exact: true })).toHaveCount(0);
  });
});
