import { expect, test } from '@playwright/test';
import fs from 'node:fs';
import { waitForApiCall } from '../fixtures/api';
import { ADMIN_USER, RETAIL_USER, type TestUser } from '../fixtures/test-users';

/** Reads a user's access token out of the storageState global-setup saved. */
function readAccessToken(user: TestUser): string {
  const state = JSON.parse(fs.readFileSync(user.storageStatePath, 'utf-8')) as {
    origins: Array<{ localStorage: Array<{ name: string; value: string }> }>;
  };
  const entry = state.origins?.[0]?.localStorage?.find((e) => e.name === 'access_token');
  if (!entry) throw new Error(`No access_token in ${user.email} storageState — did global-setup run?`);
  return entry.value;
}

/**
 * Verified-purchaser reviews (frontend/src/containers/ProductDetails/ProductDetails.tsx ->
 * GET/POST/DELETE /api/reviews, docs/specs/product-reviews.md). Covers the frontend integration
 * added on top of the reviews backend: the retail user gets a real order against the product in
 * beforeAll so they pass the purchase-verification gate; the admin user deliberately does not, to
 * exercise the 403 rejection path.
 */
test.describe('Product reviews', () => {
  let productId: string;

  test.beforeAll(async ({ request }) => {
    const products = (await (await request.get('/api/products?page=0&size=1')).json()) as Array<{
      uuid: string;
      title: string;
      price: number;
    }>;
    expect(products.length).toBeGreaterThan(0);
    const product = products[0];
    productId = product.uuid;

    // The order has to be *paid for*, not merely placed. Placing one costs nothing and can be
    // abandoned, which is why the backend gate counts only PAID/SHIPPED/DELIVERED - so a beforeAll
    // that stops at INITIATED establishes nothing this spec needs.
    //
    // It stopped at INITIATED until now, and the tests below still passed: the order-lifecycle
    // spec drives *the same first product* to PAID for *this same buyer*, in parallel, and this
    // spec was silently riding on it. Green, and asserting nothing about its own setup - run this
    // file alone and it fails.
    const token = readAccessToken(RETAIL_USER);
    const auth = { Authorization: `Bearer ${token}` };

    const created = await request.post('/api/orders', {
      headers: auth,
      data: {
        products: [{ product_id: product.uuid, title: product.title, qty: 1, price: product.price }],
      },
    });
    expect(created.ok(), `could not place the order this spec needs: ${created.status()}`).toBe(true);
    const orderId = ((await created.json()) as { id: string }).id;

    const confirmed = await request.post(`/api/orders/${orderId}/confirm`, {
      headers: auth,
      data: { payment_method: 'pm_card_visa' },
    });
    expect(confirmed.status(), 'the order was not committed, so it can never be paid').toBe(200);

    // Confirm returns once payment has been *requested*. Reaching PAID takes the stock step, the
    // capture and the reply back, across three services and a broker - so this waits for the
    // status rather than assuming it.
    await expect
      .poll(
        async () => {
          const res = await request.post('/api/orders/search', {
            headers: auth,
            data: { user_id: RETAIL_USER.email, offset: 0, limit: 50 },
          });
          if (!res.ok()) return `search failed: ${res.status()}`;
          const body = (await res.json()) as { y: Array<{ id: string; status: string }> };
          return body.y.find((o) => o.id === orderId)?.status ?? 'not found';
        },
        {
          message: `order ${orderId} never reached PAID, so the reviewer is not a verified purchaser`,
          timeout: 60_000,
          // Every tick is a real search against orders-service, and products-service checks
          // purchase eligibility through that same endpoint under a 2s deadline. Polling once a
          // second competes with the thing this setup exists to enable.
          intervals: [2_000],
        },
      )
      .toBe('PAID');
  });

  test.describe('anonymous visitor', () => {
    test('sees a login prompt and no write-review form', async ({ page }) => {
      await page.goto(`/product/${productId}`);
      await expect(page.getByRole('heading', { name: 'Reviews' })).toBeVisible();
      await expect(page.getByText('to write a review')).toBeVisible();
      await expect(page.getByRole('button', { name: 'Submit review' })).toHaveCount(0);
    });
  });

  test.describe('non-purchaser', () => {
    test.use({ storageState: ADMIN_USER.storageStatePath });

    test('is rejected with 403 when submitting a review for a product they have not bought', async ({
      page,
    }) => {
      await page.goto(`/product/${productId}`);
      await page.getByRole('button', { name: '3 stars' }).click();

      const [response] = await Promise.all([
        waitForApiCall(page, 'POST', /\/reviews$/),
        page.getByRole('button', { name: 'Submit review' }).click(),
      ]);
      expect(response.status()).toBe(403);
      await expect(page.getByRole('heading', { name: 'Your review' })).toHaveCount(0);
    });
  });

  // Submit-then-delete is a single stateful flow against one product's one review row
  // (upsert on product_id + reviewer) — must run in order, not in parallel.
  test.describe.serial('verified purchaser', () => {
    test.use({ storageState: RETAIL_USER.storageStatePath });

    // Cleanup belongs to this group, not the file. Under `fullyParallel` Playwright dispatches
    // tests individually, so a file-level afterAll runs once per group of tests it happens to
    // schedule together — and the other groups' copies would delete this group's review out from
    // under it, between the submit and the delete below.
    test.afterAll(async ({ request }) => {
      // Best-effort, so a re-run starts from an empty review list for this product.
      const token = readAccessToken(RETAIL_USER);
      const reviews = (await (await request.get(`/api/reviews?product_id=${productId}`)).json()) as Array<{
        id: string;
        reviewer: string;
      }>;
      for (const review of reviews.filter((r) => r.reviewer === RETAIL_USER.email)) {
        await request.delete(`/api/reviews/${review.id}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
      }
    });

    test('submits a review and sees the product rating and review count update', async ({ page }) => {
      await page.goto(`/product/${productId}`);
      await expect(page.getByRole('heading', { name: 'Write a review' })).toBeVisible();

      await page.getByRole('button', { name: '5 stars' }).click();
      await page.getByPlaceholder(/What did you think/).fill('Sturdy and handsome.');

      const [response] = await Promise.all([
        waitForApiCall(page, 'POST', /\/reviews$/),
        page.getByRole('button', { name: 'Submit review' }).click(),
      ]);
      expect(response.status()).toBe(200);

      await expect(page.getByRole('heading', { name: 'Your review' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Update review' })).toBeVisible();
      await expect(page.getByText('5.0 · 1 review')).toBeVisible();
    });

    test('deletes their own review, reverting the product to no reviews', async ({ page }) => {
      await page.goto(`/product/${productId}`);
      await expect(page.getByRole('heading', { name: 'Your review' })).toBeVisible();

      await page.getByRole('button', { name: 'Delete review' }).click();
      await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();

      await expect(page.getByRole('heading', { name: 'Write a review' })).toBeVisible();
      // The star summary says "No reviews yet" too, so match the review list's own empty state
      // rather than the substring both share.
      await expect(page.getByText('No reviews yet — be the first.')).toBeVisible();
    });
  });
});
