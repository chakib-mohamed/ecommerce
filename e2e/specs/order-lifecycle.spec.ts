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
 * The order lifecycle, end to end and through the real UI.
 *
 * Every other layer of testing stops at a service boundary. The per-service tests prove each
 * participant behaves; the contract tests prove both ends of every saga message agree on the same
 * JSON. Neither proves a message ever *arrives*: payment-service once wrote its replies to an
 * outbox with no relay attached, and every one of its tests passed, because they assert the row is
 * written. This spec is the only thing that fails when the chain is broken rather than the links.
 *
 * Reaching PAID requires, in order: the browser confirming the order, orders-service opening the
 * stock step, products-service answering it, orders-service opening the payment step,
 * payment-service charging the provider and replying, and orders-service consuming that reply.
 * Nothing here asserts any of those individually - the final status is only reachable if all of
 * them happened.
 */
test.describe('Order lifecycle', () => {
  test('places an order through checkout, confirms it, and it reaches PAID', async ({
    page,
    request,
  }) => {
    await page.goto('/browse');
    // Add-to-cart is hover-revealed in the real UI; force the click so this spec is not coupled
    // to hover timing (same reasoning as checkout.spec.ts).
    await page.getByRole('button', { name: 'Add to cart' }).first().click({ force: true });

    await page.goto('/checkout');
    await page.getByPlaceholder('you@email.com').fill(RETAIL_USER.email);
    await page.getByPlaceholder('Your name').fill('E2E Lifecycle Buyer');
    await page.getByPlaceholder('Street address').fill('123 Test Street');
    await page.getByPlaceholder('City').fill('Springfield');
    await page.getByPlaceholder('ZIP').fill('12345');

    // A payment method has to be chosen before the order can be committed: confirm carries an
    // opaque reference to it, and the contract rejects a blank one.
    await page.getByRole('radio', { name: 'Visa' }).check();

    const placeOrderButton = page.getByRole('button', { name: /Place order/ });
    await expect(placeOrderButton).toBeEnabled();

    // Both calls the commit makes, awaited together so neither can be missed in the race between
    // them: create, then confirm against the id create returned.
    const [createResponse, confirmResponse] = await Promise.all([
      waitForApiCall(page, 'POST', /\/orders$/),
      waitForApiCall(page, 'POST', /\/orders\/[^/]+\/confirm$/),
      placeOrderButton.click(),
    ]);

    expect(createResponse.ok()).toBe(true);
    const orderId = ((await createResponse.json()) as { id: string }).id;
    expect(orderId).toBeTruthy();

    // 200 here means committed and payment requested - not paid. The outcome arrives later, which
    // is exactly why the assertion below polls rather than reading the response body.
    expect(confirmResponse.status()).toBe(200);
    expect(new URL(confirmResponse.url()).pathname).toContain(orderId);

    await expect(page).toHaveURL(/\/confirm$/);

    // The saga runs across four services and a broker, so the wait is generous - but it is a wait
    // for a *terminal* state, not a sleep. CANCELLED is accepted as a stopping point so a declined
    // or timed-out payment fails this spec with the status it actually reached, rather than with
    // an expired timeout that says nothing about why.
    const token = readAccessToken();
    const statusOf = async (): Promise<string> => {
      const res = await request.post('/api/orders/search', {
        headers: { Authorization: `Bearer ${token}` },
        data: { user_id: RETAIL_USER.email, offset: 0, limit: 50 },
      });
      if (!res.ok()) return `search failed: ${res.status()}`;
      const body = (await res.json()) as { y: Array<{ id: string; status: string }> };
      return body.y.find((o) => o.id === orderId)?.status ?? 'not found';
    };

    await expect
      .poll(statusOf, {
        message: `order ${orderId} never reached a terminal state`,
        timeout: 60_000,
        intervals: [1_000],
      })
      .toMatch(/^(PAID|CANCELLED)$/);

    expect(await statusOf(), 'the order was cancelled rather than paid').toBe('PAID');
  });
});
