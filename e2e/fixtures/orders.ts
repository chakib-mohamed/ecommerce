import { expect, type APIRequestContext } from '@playwright/test';
import { readAccessToken, type TestUser } from './test-users';

export interface PaidOrder {
  orderId: string;
  productId: string;
}

/** Bearer headers for a user whose session global-setup saved. */
export function authFor(user: TestUser): { Authorization: string } {
  return { Authorization: `Bearer ${readAccessToken(user)}` };
}

/**
 * Buys one product, all the way to PAID.
 *
 * <p>Placing an order costs nothing and can be abandoned, so a setup that stops at `INITIATED`
 * establishes nothing about a paid order - and the states that follow are reachable only from
 * `PAID`. Confirm returns once payment has been *requested*; reaching `PAID` takes the stock step,
 * the capture and the reply back, across three services and a broker, so this waits for the status
 * rather than assuming it.
 */
export async function purchaseToPaid(
  request: APIRequestContext,
  buyer: TestUser,
): Promise<PaidOrder> {
  const headers = authFor(buyer);

  const catalogue = await request.get('/api/products?page=0&size=1');
  const products = (await catalogue.json()) as Array<{
    uuid: string;
    title: string;
    price: number;
  }>;
  expect(products.length, 'the catalogue is empty, so nothing can be bought').toBeGreaterThan(0);
  const product = products[0];

  const created = await request.post('/api/orders', {
    headers,
    data: {
      products: [{ product_id: product.uuid, title: product.title, qty: 1, price: product.price }],
    },
  });
  expect(created.ok(), `could not place an order: ${created.status()}`).toBe(true);
  const orderId = ((await created.json()) as { id: string }).id;

  const confirmed = await request.post(`/api/orders/${orderId}/confirm`, {
    headers,
    data: { payment_method: 'pm_card_visa' },
  });
  expect(confirmed.status(), 'the order was not committed, so it can never be paid').toBe(200);

  await expect
    .poll(() => statusOf(request, buyer, orderId), {
      message: `order ${orderId} never reached PAID`,
      timeout: 60_000,
      // Every tick is a real search against orders-service. Polling once a second competes with
      // the saga this is waiting on.
      intervals: [2_000],
    })
    .toBe('PAID');

  return { orderId, productId: product.uuid };
}

/** The order's current status, or why it could not be read. */
export async function statusOf(
  request: APIRequestContext,
  buyer: TestUser,
  orderId: string,
): Promise<string> {
  const res = await request.post('/api/orders/search', {
    headers: authFor(buyer),
    data: { user_id: buyer.email, offset: 0, limit: 50 },
  });
  if (!res.ok()) return `search failed: ${res.status()}`;
  const body = (await res.json()) as { y: Array<{ id: string; status: string }> };
  return body.y.find((o) => o.id === orderId)?.status ?? 'not found';
}
