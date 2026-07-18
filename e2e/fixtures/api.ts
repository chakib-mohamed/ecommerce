import type { Page, Response } from '@playwright/test';

/**
 * Waits for a specific backend API call and returns its response, so specs
 * can assert on the exact request the app made (URL/query params) and the
 * status code — not just on rendered DOM state.
 *
 * `pathPattern` matches against the request URL's pathname + search.
 */
export function waitForApiCall(
  page: Page,
  method: string,
  pathPattern: RegExp,
): Promise<Response> {
  return page.waitForResponse((res) => {
    if (res.request().method() !== method) return false;
    const url = new URL(res.url());
    return pathPattern.test(url.pathname + url.search);
  });
}
