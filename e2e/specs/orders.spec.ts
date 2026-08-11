import { expect, test } from '@playwright/test';
import fs from 'node:fs';
import { waitForApiCall } from '../fixtures/api';
import { RETAIL_USER } from '../fixtures/test-users';

test.use({ storageState: RETAIL_USER.storageStatePath });

/** Reads the retail user's access token out of the storageState global-setup saved. */
function readAccessToken(): string {
  const state = JSON.parse(fs.readFileSync(RETAIL_USER.storageStatePath, 'utf-8')) as {
    origins: Array<{ localStorage: Array<{ name: string; value: string }> }>;
  };
  const entry = state.origins?.[0]?.localStorage?.find((e) => e.name === 'access_token');
  if (!entry) throw new Error('No access_token in retail storageState — did global-setup run?');
  return entry.value;
}

/**
 * Order history, scoped to the authenticated buyer
 * (frontend/src/containers/Orders/Orders.tsx -> POST /api/orders/search).
 * Exercises the "own orders by authenticated user" fix (commit f160317).
 */
test.describe('Order history', () => {
  let placedOrderId: string;

  test.beforeAll(async ({ request }) => {
    const token = readAccessToken();
    // Order against a real seeded product rather than a fabricated id.
    const products = (await (await request.get('/api/products?page=0&size=1')).json()) as Array<{
      uuid: string;
      title: string;
      price: number;
    }>;
    expect(products.length).toBeGreaterThan(0);
    const product = products[0];

    const res = await request.post('/api/orders', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        products: [{ product_id: product.uuid, title: product.title, qty: 1, price: product.price }],
      },
    });
    expect(res.ok()).toBe(true);
    placedOrderId = ((await res.json()) as { id: string }).id;
  });

  test("shows the buyer's own previously placed order, scoped to their email", async ({ page }) => {
    const [response] = await Promise.all([
      waitForApiCall(page, 'POST', /\/orders\/search$/),
      page.goto('/orders'),
    ]);
    expect(response.status()).toBe(200);

    // Regression check for f160317: the search request must be scoped to
    // *this* buyer's email, not fetch every user's orders.
    const requestBody = response.request().postDataJSON() as { user_id: string };
    expect(requestBody.user_id).toBe(RETAIL_USER.email);

    // Paged through rather than asserted on the first screen. The history shows five at a time and
    // this buyer is shared: checkout and the lifecycle specs place their own orders for the same
    // account, in parallel, at times this spec does not control. Asserting on page one made the
    // result depend on how many of those happened to land first - it passed while the suite was
    // small and would have started failing on a spec that never touched this page.
    const card = page.getByText(`Order #${placedOrderId.slice(-6)}`);
    const next = page.getByRole('button', { name: /Next/ });
    for (;;) {
      if (await card.isVisible()) break;
      if (!(await next.isVisible()) || !(await next.isEnabled())) {
        throw new Error(`order ${placedOrderId} is not on any page of the buyer's history`);
      }
      await Promise.all([waitForApiCall(page, 'POST', /\/orders\/search$/), next.click()]);
    }
    await expect(card).toBeVisible();
  });
});
