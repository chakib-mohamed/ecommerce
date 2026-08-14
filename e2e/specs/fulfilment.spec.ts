import { expect, test } from '@playwright/test';
import { authFor, purchaseToPaid, statusOf } from '../fixtures/orders';
import { ADMIN_USER, specUser } from '../fixtures/test-users';

/**
 * Shipping and delivering an order (docs/specs/order-fulfilment.md).
 *
 * <p>Driven over the API rather than through the UI, because there is no back-office screen for
 * fulfilment yet - these endpoints are the deliverable and whatever calls them is a later
 * question. What that still covers, and unit tests cannot: the gateway routes both paths, and the
 * role check works against a token minted by a *real login* rather than one a test framework
 * fabricated. Until this ran, nothing had proved `@RolesAllowed("admin")` was satisfiable by an
 * actual session - and for the entire life of this platform before it, it would not have been:
 * the token carried no roles at all.
 */
test.describe('Order fulfilment', () => {
  // Serial, against the suite's fullyParallel default: this is one order walking its lifecycle,
  // and the states are reachable only in order. Run in parallel these would race for the same
  // order - the double-delivery case needs the delivery to have happened, and would otherwise
  // pass or fail on which worker got there first. The alternative, a fresh paid order per test,
  // costs four full saga round-trips to assert the same four things.
  test.describe.configure({ mode: 'serial' });

  let orderId: string;

  test.beforeAll(async ({ request }) => {
    // This spec's own buyer, so the order it drives to DELIVERED lands in nobody else's history.
    ({ orderId } = await purchaseToPaid(request, specUser('fulfilment')));
  });

  test('an administrator ships a paid order and then delivers it', async ({ request }) => {
    const admin = authFor(ADMIN_USER);

    const shipped = await request.post(`/api/orders/${orderId}/ship`, { headers: admin });
    expect(shipped.status(), 'a paid order should be shippable').toBe(200);
    expect(((await shipped.json()) as { status: string }).status).toBe('SHIPPED');

    const delivered = await request.post(`/api/orders/${orderId}/deliver`, { headers: admin });
    expect(delivered.status(), 'a shipped order should be deliverable').toBe(200);
    expect(((await delivered.json()) as { status: string }).status).toBe('DELIVERED');

    // Read back through the buyer's own view of their order: the point of fulfilment is that the
    // buyer can see where their parcel got to, and until now every order stopped reading PAID.
    expect(await statusOf(request, specUser('fulfilment'), orderId)).toBe('DELIVERED');
  });

  test('the same order cannot be delivered twice', async ({ request }) => {
    // Deliberately not idempotent, and this runs after the test above has already delivered it.
    // A 200 here would report a delivery that did not happen.
    const again = await request.post(`/api/orders/${orderId}/deliver`, { headers: authFor(ADMIN_USER) });
    expect(again.status(), 'delivered is final').toBe(409);
  });

  test('a buyer may not ship their own order', async ({ request }) => {
    // The case most likely to be got wrong by copying confirm and cancel, which pass exactly this
    // ownership check. Dispatching is a warehouse fact, so owning the order is not the permission
    // being asked for - and this buyer owns it.
    const buyer = specUser('fulfilment');
    const refused = await request.post(`/api/orders/${orderId}/ship`, { headers: authFor(buyer) });
    expect(refused.status(), 'being the owner is not being an administrator').toBe(403);
  });

  test('an unauthenticated caller may not ship anything', async ({ request }) => {
    const refused = await request.post(`/api/orders/${orderId}/ship`);
    expect([401, 403]).toContain(refused.status());
  });
});
