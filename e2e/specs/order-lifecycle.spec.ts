import { expect, test } from '@playwright/test';
import { waitForApiCall } from '../fixtures/api';
import { readAccessToken, specUser, storageStateFor } from '../fixtures/test-users';

test.use({ storageState: storageStateFor('lifecycle') });

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
    await page.getByPlaceholder('you@email.com').fill(specUser('lifecycle').email);
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
    const buyer = specUser('lifecycle');
    const token = readAccessToken(buyer);
    const statusOf = async (): Promise<string> => {
      const res = await request.post('/api/orders/search', {
        headers: { Authorization: `Bearer ${token}` },
        data: { user_id: buyer.email, offset: 0, limit: 50 },
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

  test('a confirm whose answer is lost does not place a second order', async ({ page }) => {
    // Counted from this page's own requests rather than from the orders in the database. The
    // database is shared: the suite runs fully parallel and other specs place orders for this same
    // buyer, so a before/after count measures them too and fails for reasons that have nothing to
    // do with this. What is actually being asserted is that the browser did not ask for a second
    // order, and the browser is right here.
    let ordersCreated = 0;
    page.on('request', (req) => {
      if (req.method() === 'POST' && new URL(req.url()).pathname.endsWith('/api/orders')) {
        ordersCreated += 1;
      }
    });

    await page.goto('/browse');
    await page.getByRole('button', { name: 'Add to cart' }).first().click({ force: true });

    await page.goto('/checkout');
    await page.getByPlaceholder('you@email.com').fill(specUser('lifecycle').email);
    await page.getByPlaceholder('Your name').fill('E2E Lost Answer');
    await page.getByPlaceholder('Street address').fill('123 Test Street');
    await page.getByPlaceholder('City').fill('Springfield');
    await page.getByPlaceholder('ZIP').fill('12345');
    await page.getByRole('radio', { name: 'Visa' }).check();

    // The failure this is about: the server receives the confirm and commits the order, and the
    // answer never arrives. From the browser it is indistinguishable from the request never
    // landing - which is why the naive response is to try again, and why trying again used to
    // create a second order that was separately reserved and separately charged.
    let swallowedOne = false;
    await page.route('**/api/orders/*/confirm', async (route) => {
      if (swallowedOne) {
        await route.continue();
        return;
      }
      swallowedOne = true;
      await route.fetch();
      await route.abort('failed');
    });

    const placeOrder = page.getByRole('button', { name: /Place order/ });
    await placeOrder.click();

    // Where the page lands after a request that was killed mid-flight is a race this test starts
    // and does not control, so it is not asserted. Two settled states are legitimate: still on
    // checkout with the button live, which is the buyer retrying by hand; or already on the
    // confirmation page, because the answer arrived after all. Pinning one of them is what made
    // this test flaky - it failed on the button having been replaced by the empty-cart branch
    // mid-render, which says nothing about whether a second order was placed.
    const onConfirm = () => /\/confirm$/.test(new URL(page.url()).pathname);

    await expect
      .poll(async () => (onConfirm() ? 'confirmed' : await placeOrder.isVisible()), {
        timeout: 20_000,
        intervals: [250],
        message: 'checkout settled into neither a retryable form nor a confirmation',
      })
      .not.toBe(false);

    if (!onConfirm()) {
      // The retry commits the order that already exists rather than creating another - being told
      // it is already committed is the success path, not a failure.
      await placeOrder.click();
    }

    await expect(page).toHaveURL(/\/confirm$/, { timeout: 20_000 });

    // The interception outlives the assertions above when the confirm it swallowed is still in
    // flight at teardown, which surfaces as a "Test ended" error against the route callback rather
    // than against anything this test asserts.
    await page.unrouteAll({ behavior: 'ignoreErrors' });

    expect(ordersCreated,
      'the retry placed a second order - two orders means two reservations and two charges for '
      + 'one basket, and no idempotency key can catch that because they are legitimately different '
      + 'orders').toBe(1);
  });
});
