import { expect, test } from '@playwright/test';
import { waitForApiCall } from '../fixtures/api';

/**
 * Catalog browsing, category filtering, and load-more pagination
 * (frontend/src/containers/Browse/Browse.tsx, frontend/src/lib/use-product-page.ts).
 * This is this branch's HEAD-commit feature — server-side category filtering
 * and load-more pagination — and runs unauthenticated (guest browsing).
 */
test.describe('Catalog browsing', () => {
  test('renders real products with price and category on /browse', async ({ page }) => {
    const [response] = await Promise.all([
      waitForApiCall(page, 'GET', /\/products\?page=0&size=12$/),
      page.goto('/browse'),
    ]);
    expect(response.status()).toBe(200);

    await expect(page.getByText(/\d+\+?\s+items?/)).toBeVisible();
    await expect(page.getByText('Nothing here yet')).toHaveCount(0);

    const addToCartButtons = page.getByRole('button', { name: 'Add to cart' });
    expect(await addToCartButtons.count()).toBeGreaterThan(0);
  });

  test('category filter navigates and fires a server-side filtered request', async ({
    page,
    request,
  }) => {
    // Resolve a real category id/name from the API — don't hardcode today's seed.
    const categories = await (await request.get('/api/categories')).json();
    expect(Array.isArray(categories) && categories.length).toBeTruthy();
    const target = categories[0] as { id: number | string; label: string };

    await page.goto('/browse');

    const [response] = await Promise.all([
      waitForApiCall(page, 'GET', new RegExp(`/products\\?page=0&size=12&category_id=${target.id}$`)),
      page.locator('aside').getByRole('button', { name: target.label, exact: true }).click(),
    ]);
    expect(response.status()).toBe(200);
    await expect(page).toHaveURL(new RegExp(`/browse/${target.id}$`));

    const body = (await response.json()) as Array<{ category_id: number | string }>;
    for (const p of body) {
      expect(String(p.category_id)).toBe(String(target.id));
    }
  });

  test('load more appends the next page and disappears once a short page returns', async ({
    page,
  }) => {
    await Promise.all([
      waitForApiCall(page, 'GET', /\/products\?page=0&size=12$/),
      page.goto('/browse'),
    ]);

    const countBefore = await page.getByRole('button', { name: 'Add to cart' }).count();
    const loadMoreButton = page.getByRole('button', { name: /Load more/ });

    if ((await loadMoreButton.count()) === 0) {
      test.skip(true, 'Seed catalog fits in one page — nothing to load more.');
    }

    // Mock just the second page, so this assertion doesn't depend on today's
    // exact seed product count (which happens to be a single exact page as of
    // writing — see docs/specs/e2e-test-suite.md's assertion-philosophy note).
    // A short (< pageSize), non-empty page here exercises both "append" and
    // "hasMore flips false" in one shot.
    await page.route(/\/api\/products\?page=1.*/, (route) =>
      route.fulfill({
        json: [
          {
            uuid: 'e2e-load-more-fixture',
            title: 'E2E Load More Fixture',
            description: 'Injected by catalog.spec.ts to test pagination append.',
            price: 1,
            stock: 1,
            category_id: 0,
          },
        ],
      }),
    );

    await loadMoreButton.click();
    await expect(page.getByRole('button', { name: 'Add to cart' })).toHaveCount(countBefore + 1);
    await expect(page.getByRole('button', { name: /Load more/ })).toHaveCount(0);
  });
});
