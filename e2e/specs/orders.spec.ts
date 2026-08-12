import { expect, test } from '@playwright/test';
import { waitForApiCall } from '../fixtures/api';
import { readAccessToken, specUser, storageStateFor } from '../fixtures/test-users';

test.use({ storageState: storageStateFor('orders') });

/**
 * Order history, scoped to the authenticated buyer
 * (frontend/src/containers/Orders/Orders.tsx -> POST /api/orders/search).
 * Exercises the "own orders by authenticated user" fix (commit f160317).
 */
test.describe('Order history', () => {
  let placedOrderId: string;

  test.beforeAll(async ({ request }) => {
    const buyer = specUser('orders');
    const token = readAccessToken(buyer);
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
    expect(requestBody.user_id).toBe(specUser('orders').email);

    // This buyer is registered fresh for this run and places exactly one order, so its history is
    // a single page containing exactly that order. The previous version of this test walked the
    // pager, because every buyer spec shared one account and five-per-page meant the order could
    // be anywhere - which was a workaround for shared state rather than a property worth asserting.
    await expect(page.getByText('1 order')).toBeVisible();
    await expect(page.getByText(`Order #${placedOrderId.slice(-6)}`)).toBeVisible();
  });
});
