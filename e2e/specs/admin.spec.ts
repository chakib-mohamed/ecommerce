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
test.describe('Admin back-office', () => {
  test('/admin/products renders products with live stock/price and edit/delete controls', async ({
    page,
  }) => {
    await page.goto('/admin/products');
    await expect(page.getByRole('columnheader', { name: 'Price' }).first()).toBeVisible();
    await expect(page.getByRole('columnheader', { name: 'Stock' }).first()).toBeVisible();
    expect(await page.getByRole('button', { name: 'Edit' }).count()).toBeGreaterThan(0);
    expect(await page.getByRole('button', { name: 'Delete' }).count()).toBeGreaterThan(0);
  });

  test('/admin/categories renders the hierarchy and supports adding + deleting a subcategory', async ({
    page,
  }) => {
    await page.goto('/admin/categories');
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
