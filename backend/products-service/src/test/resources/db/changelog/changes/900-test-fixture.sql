--liquibase formatted sql

--changeset chakib:900-test-fixture context:test

-- Test-only catalog fixture. Reached only through db.changelog-test.xml, which the test profile
-- points Liquibase at; it is not referenced by the production changelog and does not ship.
--
-- A known two-level category tree and a product filed under the leaf, so the read-side derivation
-- assertions (category_id = parent, subcategory_id = leaf) have fixed ids to work against rather
-- than depending on whatever the seed data happens to contain.
--
-- Ids are in the 900s to stay clear of the seed data, which uses 10-29.
--
-- The context attribute is kept as documentation of intent, but it is not what keeps these rows out
-- of a real database: Liquibase applies every change when no contexts are set at runtime, and the
-- Compose stack sets none. Being unreachable from the production changelog is what does it.
--
-- This lived in src/test/resources/import-test.sql, loaded by Hibernate's sql-load-script, which
-- only runs when Hibernate builds the schema itself. That is exactly what the tests stopped doing:
-- they now run against the schema the migrations produce, which is the whole point - a schema built
-- from the entities agrees with them by construction and can never disagree.
--
-- Note for future edits: a comment line here must not begin with a Liquibase keyword. The parser
-- reads "-- changeset ..." as a directive, so an ordinary sentence starting with that word fails
-- the whole changelog at startup.
INSERT INTO category (id, label, parent_id) VALUES (900, 'TestParent', null);
INSERT INTO category (id, label, parent_id) VALUES (901, 'TestChild', 900);

INSERT INTO product (id, uuid, title, description, price)
VALUES (900, 'b0000000-0000-0000-0000-000000000901', 'Test Leaf Product', 'filed under TestChild', 100.0);

INSERT INTO product_category (product_id, category_id) VALUES (900, 901);
