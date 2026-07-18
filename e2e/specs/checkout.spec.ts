import { expect, test } from '@playwright/test';
import { waitForApiCall } from '../fixtures/api';
import { RETAIL_USER } from '../fixtures/test-users';

test.use({ storageState: RETAIL_USER.storageStatePath });

/**
 * Add to cart -> checkout -> place a real order
 * (frontend/src/containers/Checkout/Checkout.tsx -> POST /api/orders).
 */
test('adds a product to cart, completes checkout, and lands on /confirm with a real order id', async ({
  page,
}) => {
  await page.goto('/browse');
  // The add-to-cart button only shows on hover in the real UI (a UX detail
  // this spec isn't targeting); force the click so the test isn't coupled
  // to hover-reveal timing.
  await page.getByRole('button', { name: 'Add to cart' }).first().click({ force: true });

  await page.goto('/checkout');
  await page.getByPlaceholder('you@email.com').fill(RETAIL_USER.email);
  await page.getByPlaceholder('Your name').fill('E2E Test Buyer');
  await page.getByPlaceholder('Street address').fill('123 Test Street');
  await page.getByPlaceholder('City').fill('Springfield');
  await page.getByPlaceholder('ZIP').fill('12345');

  const placeOrderButton = page.getByRole('button', { name: /Place order/ });
  await expect(placeOrderButton).toBeEnabled();

  const [response] = await Promise.all([
    waitForApiCall(page, 'POST', /\/orders$/),
    placeOrderButton.click(),
  ]);
  expect(response.ok()).toBe(true);
  const order = (await response.json()) as { id: string };
  expect(order.id).toBeTruthy();

  await expect(page).toHaveURL(/\/confirm$/);
  await expect(page.getByText('Thank you!')).toBeVisible();
  await expect(page.getByText(order.id)).toBeVisible();
});
