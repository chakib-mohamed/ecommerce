import fs from 'node:fs';

/**
 * Seeded test credentials — the two accounts `mongo-init` creates on every
 * `docker compose up` (backend/dev-scripts/mongodb/03-users.js, idempotent upsert).
 *
 * These exist because two specs need a *known, fixed* credential pair: auth.spec drives the real
 * login form (including a deliberately wrong password), and admin.spec needs the admin role.
 * Nothing else should use them — see below.
 */

export interface TestUser {
  email: string;
  password: string;
  role: 'customer' | 'admin';
  /** Where global-setup saves this user's authenticated storageState. */
  storageStatePath: string;
}

export const RETAIL_USER: TestUser = {
  email: 'retail@ecommerce.test',
  password: 'Retail123!',
  role: 'customer',
  storageStatePath: '.auth/retail.json',
};

export const ADMIN_USER: TestUser = {
  email: 'admin@ecommerce.test',
  password: 'Admin123!',
  role: 'admin',
  storageStatePath: '.auth/admin.json',
};

/**
 * A buyer per spec, registered fresh for every run.
 *
 * <p>Everything that places an order gets its own account, because an order history is mutable
 * shared state and these specs run in parallel. Sharing one buyer produced three separate
 * failures: a history that paged differently depending on which spec finished first; a review
 * test that passed only because *another* spec had paid for the same product for the same buyer,
 * and would have failed run alone; and concurrent sagas contending on one account.
 *
 * <p>The emails carry a per-run suffix so a stack that is not torn down between runs does not
 * hand the next run a history full of the last one's orders - which would reintroduce the same
 * problem one run later.
 */
export const SPEC_BUYERS = [
  'smoke',
  'checkout',
  'lifecycle',
  'orders',
  'reviews-buyer',
  'reviews-nonbuyer',
  'fulfilment',
] as const;

export type SpecBuyer = (typeof SPEC_BUYERS)[number];

/** Where global-setup records the addresses it generated, so specs can read them back. */
const REGISTRY_PATH = '.auth/spec-users.json';

/**
 * Where a spec buyer's session is saved. Deterministic, so it can be used at module scope in
 * `test.use({ storageState })` before global-setup has run.
 */
export function storageStateFor(slug: SpecBuyer): string {
  return `.auth/spec-${slug}.json`;
}

/** The password every generated account uses. Must clear the 8-character minimum on sign-up. */
export const SPEC_BUYER_PASSWORD = 'E2ePassw0rd!';

/** Builds this run's addresses. Called once, by global-setup. */
export function generateSpecUsers(runId: string): Record<SpecBuyer, TestUser> {
  const users = {} as Record<SpecBuyer, TestUser>;
  for (const slug of SPEC_BUYERS) {
    users[slug] = {
      email: `e2e-${slug}-${runId}@ecommerce.test`,
      password: SPEC_BUYER_PASSWORD,
      role: 'customer',
      storageStatePath: storageStateFor(slug),
    };
  }
  return users;
}

export function writeSpecUserRegistry(users: Record<SpecBuyer, TestUser>): void {
  fs.writeFileSync(REGISTRY_PATH, JSON.stringify(users, null, 2));
}

/**
 * This run's account for a spec.
 *
 * <p>Read lazily rather than at import time: the registry is written by global-setup, which runs
 * after the spec files have been loaded. Safe to call from a test or a hook, not from module
 * scope - use {@link storageStateFor} there.
 */
export function specUser(slug: SpecBuyer): TestUser {
  if (!fs.existsSync(REGISTRY_PATH)) {
    throw new Error(
      `No ${REGISTRY_PATH} — did global-setup run? Specs get a freshly registered buyer per run.`,
    );
  }
  const registry = JSON.parse(fs.readFileSync(REGISTRY_PATH, 'utf-8')) as Record<string, TestUser>;
  const user = registry[slug];
  if (!user) throw new Error(`No buyer registered for '${slug}' — is it listed in SPEC_BUYERS?`);
  return user;
}

/** Reads a user's access token out of the storageState global-setup saved. */
export function readAccessToken(user: TestUser): string {
  const state = JSON.parse(fs.readFileSync(user.storageStatePath, 'utf-8')) as {
    origins: Array<{ localStorage: Array<{ name: string; value: string }> }>;
  };
  const entry = state.origins?.[0]?.localStorage?.find((e) => e.name === 'access_token');
  if (!entry) throw new Error(`No access_token in ${user.email} storageState — did global-setup run?`);
  return entry.value;
}
