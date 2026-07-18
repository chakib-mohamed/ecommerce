-- Test-only catalog fixture for the product-model-extension integration tests.
--
-- The test profile builds its schema from entities (Hibernate drop-and-create), so the
-- Liquibase dev-seed (004-rich-dev-data) is not present here. This script seeds a known
-- two-level category tree and a product filed under the leaf, so the read-side derivation
-- assertions (category_id = parent, subcategory_id = leaf) run against fixed ids.
INSERT INTO category (id, label, parent_id) VALUES (900, 'TestParent', null);
INSERT INTO category (id, label, parent_id) VALUES (901, 'TestChild', 900);

INSERT INTO product (id, uuid, title, description, price)
VALUES (900, 'b0000000-0000-0000-0000-000000000901', 'Test Leaf Product', 'filed under TestChild', 100.0);

INSERT INTO product_category (product_id, category_id) VALUES (900, 901);
