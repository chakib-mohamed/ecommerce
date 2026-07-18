/**
 * Seeded test credentials — single source of truth for the suite. Seeded by
 * `mongo-init` via backend/dev-scripts/mongodb/03-users.js on every
 * `docker compose up` (idempotent upsert, safe to rely on across runs).
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
