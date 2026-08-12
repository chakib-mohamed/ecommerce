import { request as playwrightRequest } from '@playwright/test';
import fs from 'node:fs';
import { RETAIL_USER } from './test-users';

/**
 * How long to keep trying before declaring the stack unable to sell anything. Generous, because
 * the cost of waiting is a slower run and the cost of giving up early is the whole suite failing
 * for a reason that had nothing to do with the code under test.
 */
const READY_DEADLINE_MS = 180_000;

/** Gap between attempts. Long enough that retrying is not itself load. */
const RETRY_DELAY_MS = 3_000;

/** How long one attempt waits for its order to reach PAID before being written off. */
const PAID_TIMEOUT_MS = 45_000;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** Reads the retail user's access token out of the storageState global-setup just saved. */
function accessToken(): string {
  const state = JSON.parse(fs.readFileSync(RETAIL_USER.storageStatePath, 'utf-8')) as {
    origins: Array<{ localStorage: Array<{ name: string; value: string }> }>;
  };
  const entry = state.origins?.[0]?.localStorage?.find((e) => e.name === 'access_token');
  if (!entry) throw new Error('No access_token in retail storageState — did the logins run?');
  return entry.value;
}

/**
 * One end-to-end attempt: buy something.
 *
 * @returns null when the order reached PAID, otherwise why it did not
 */
async function buyOnce(baseURL: string, token: string): Promise<string | null> {
  const api = await playwrightRequest.newContext({
    baseURL,
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });
  try {
    const catalogue = await api.get('/api/products?page=0&size=1');
    if (!catalogue.ok()) return `catalogue unavailable (${catalogue.status()})`;
    const products = (await catalogue.json()) as Array<{
      uuid: string;
      title: string;
      price: number;
    }>;
    if (!products.length) return 'catalogue is empty';
    const product = products[0];

    const created = await api.post('/api/orders', {
      data: {
        products: [{ product_id: product.uuid, title: product.title, qty: 1, price: product.price }],
      },
    });
    if (!created.ok()) return `could not place an order (${created.status()})`;
    const orderId = ((await created.json()) as { id: string }).id;

    // The step that has been failing with a 500 while the stack is cold: confirm re-checks prices
    // against products-service and price-service over HTTP before it commits anything, so it is
    // the first operation that needs three services to be genuinely usable rather than merely
    // answering their health endpoint.
    const confirmed = await api.post(`/api/orders/${orderId}/confirm`, {
      data: { payment_method: 'pm_card_visa' },
    });
    if (confirmed.status() !== 200) return `could not confirm the order (${confirmed.status()})`;

    const deadline = Date.now() + PAID_TIMEOUT_MS;
    let last = 'no status yet';
    while (Date.now() < deadline) {
      const search = await api.post('/api/orders/search', {
        data: { user_id: RETAIL_USER.email, offset: 0, limit: 50 },
      });
      if (search.ok()) {
        const body = (await search.json()) as { y: Array<{ id: string; status: string }> };
        last = body.y.find((o) => o.id === orderId)?.status ?? 'not found';
        if (last === 'PAID') return null;
        if (last === 'CANCELLED') return 'the order was cancelled rather than paid';
      } else {
        last = `search failed (${search.status()})`;
      }
      await sleep(2_000);
    }
    return `the order never reached PAID (last status: ${last})`;
  } finally {
    await api.dispose();
  }
}

/**
 * Blocks until the stack can actually take an order all the way to PAID.
 *
 * <p>Compose reports a service healthy when its health endpoint answers, which happens well before
 * its REST clients, Kafka consumers and database pools are usable. The suite used to start at
 * exactly that moment and spend its first seconds driving the heaviest path in the system through
 * a stack that was not ready - which surfaced as `confirm` returning 500, three of fifteen tests
 * needing a retry, and occasionally a hard failure.
 *
 * <p>This absorbs that window deliberately rather than detecting it: the errors it swallows are
 * the ones the specs would otherwise hit. It is not a test and asserts nothing about behaviour -
 * if it cannot complete a purchase within the deadline then every order-related spec is going to
 * fail anyway, and failing here says so in one line instead of fifteen.
 */
export async function waitUntilTheStackCanSell(baseURL: string): Promise<void> {
  const token = accessToken();
  const deadline = Date.now() + READY_DEADLINE_MS;
  const started = Date.now();
  let attempts = 0;
  let reason = 'never attempted';

  while (Date.now() < deadline) {
    attempts += 1;
    reason = (await buyOnce(baseURL, token)) ?? '';
    if (reason === '') {
      const elapsed = ((Date.now() - started) / 1000).toFixed(1);
      console.log(`[smoke] stack can complete a purchase (attempt ${attempts}, ${elapsed}s)`);
      return;
    }
    console.log(`[smoke] attempt ${attempts}: ${reason} — retrying`);
    await sleep(RETRY_DELAY_MS);
  }

  throw new Error(
    `The stack could not complete a purchase within ${READY_DEADLINE_MS / 1000}s `
      + `(${attempts} attempts, last failure: ${reason}). Every order, checkout and review spec `
      + `depends on this working, so the suite is stopping here rather than reporting it fifteen `
      + `times.`,
  );
}
