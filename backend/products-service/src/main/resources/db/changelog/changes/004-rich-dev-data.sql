-- liquibase formatted sql

-- changeset chakib:004-rich-dev-data

-- wipe flat data from changeset 002
DELETE FROM product_promotion;
DELETE FROM product_category;
DELETE FROM promotion;
DELETE FROM product;
DELETE FROM category;

-- parent categories
INSERT INTO category (id, label, parent_id) VALUES (10, 'Dining', null);
INSERT INTO category (id, label, parent_id) VALUES (11, 'Living Room', null);
INSERT INTO category (id, label, parent_id) VALUES (12, 'Bedroom', null);
INSERT INTO category (id, label, parent_id) VALUES (13, 'Outdoor', null);

-- subcategories
INSERT INTO category (id, label, parent_id) VALUES (20, 'Dining Tables', 10);
INSERT INTO category (id, label, parent_id) VALUES (21, 'Dining Chairs', 10);
INSERT INTO category (id, label, parent_id) VALUES (22, 'Sideboards', 10);
INSERT INTO category (id, label, parent_id) VALUES (23, 'Sofas', 11);
INSERT INTO category (id, label, parent_id) VALUES (24, 'Coffee Tables', 11);
INSERT INTO category (id, label, parent_id) VALUES (25, 'TV Stands', 11);
INSERT INTO category (id, label, parent_id) VALUES (26, 'Beds', 12);
INSERT INTO category (id, label, parent_id) VALUES (27, 'Nightstands', 12);
INSERT INTO category (id, label, parent_id) VALUES (28, 'Garden Tables', 13);
INSERT INTO category (id, label, parent_id) VALUES (29, 'Garden Chairs', 13);

-- products (12, stable UUIDs)
INSERT INTO product (id, description, price, title, uuid) VALUES
    (10, 'Elegant marble top dining table for 6', 899.99, 'Marble Dining Table', 'a0000000-0000-0000-0000-000000000001');
INSERT INTO product (id, description, price, title, uuid) VALUES
    (11, 'Solid oak dining table with natural finish', 649.00, 'Oak Dining Table', 'a0000000-0000-0000-0000-000000000002');
INSERT INTO product (id, description, price, title, uuid) VALUES
    (12, 'Set of 2 velvet upholstered dining chairs', 129.99, 'Velvet Dining Chair', 'a0000000-0000-0000-0000-000000000003');
INSERT INTO product (id, description, price, title, uuid) VALUES
    (13, 'Walnut wood sideboard with 3 drawers', 749.00, 'Walnut Sideboard', 'a0000000-0000-0000-0000-000000000004');
INSERT INTO product (id, description, price, title, uuid) VALUES
    (14, 'L-shape corner sofa in grey fabric', 1199.00, 'L-Shape Corner Sofa', 'a0000000-0000-0000-0000-000000000005');
INSERT INTO product (id, description, price, title, uuid) VALUES
    (15, 'Tempered glass top coffee table', 249.99, 'Glass Coffee Table', 'a0000000-0000-0000-0000-000000000006');
INSERT INTO product (id, description, price, title, uuid) VALUES
    (16, 'Industrial style TV stand with open shelves', 319.00, 'Industrial TV Stand', 'a0000000-0000-0000-0000-000000000007');
INSERT INTO product (id, description, price, title, uuid) VALUES
    (17, 'King size platform bed with headboard', 999.00, 'King Size Platform Bed', 'a0000000-0000-0000-0000-000000000008');
INSERT INTO product (id, description, price, title, uuid) VALUES
    (18, 'Solid oak nightstand with 2 drawers', 189.00, 'Solid Oak Nightstand', 'a0000000-0000-0000-0000-000000000009');
INSERT INTO product (id, description, price, title, uuid) VALUES
    (19, 'Teak outdoor garden dining table', 449.00, 'Teak Garden Table', 'a0000000-0000-0000-0000-000000000010');
INSERT INTO product (id, description, price, title, uuid) VALUES
    (20, 'Lightweight folding garden chair', 89.99, 'Folding Garden Chair', 'a0000000-0000-0000-0000-000000000011');
INSERT INTO product (id, description, price, title, uuid) VALUES
    (21, 'Natural rattan wicker coffee table', 299.00, 'Rattan Coffee Table', 'a0000000-0000-0000-0000-000000000012');

-- product_category join
INSERT INTO product_category (product_id, category_id) VALUES (10, 20);
INSERT INTO product_category (product_id, category_id) VALUES (11, 20);
INSERT INTO product_category (product_id, category_id) VALUES (12, 21);
INSERT INTO product_category (product_id, category_id) VALUES (13, 22);
INSERT INTO product_category (product_id, category_id) VALUES (14, 23);
INSERT INTO product_category (product_id, category_id) VALUES (15, 24);
INSERT INTO product_category (product_id, category_id) VALUES (16, 25);
INSERT INTO product_category (product_id, category_id) VALUES (17, 26);
INSERT INTO product_category (product_id, category_id) VALUES (18, 27);
INSERT INTO product_category (product_id, category_id) VALUES (19, 28);
INSERT INTO product_category (product_id, category_id) VALUES (20, 29);
INSERT INTO product_category (product_id, category_id) VALUES (21, 24);

-- promotions (active dates within 2025-2026)
INSERT INTO promotion (id, active_from, active_to, label, percentage_off) VALUES
    (10, '2025-06-01', '2025-08-31', 'Summer Sale', 15);
INSERT INTO promotion (id, active_from, active_to, label, percentage_off) VALUES
    (11, '2025-05-01', '2025-12-31', 'New Arrivals', 10);

-- product_promotion join
INSERT INTO product_promotion (product_id, promotion_id) VALUES (10, 10);
INSERT INTO product_promotion (product_id, promotion_id) VALUES (11, 10);
INSERT INTO product_promotion (product_id, promotion_id) VALUES (14, 11);
INSERT INTO product_promotion (product_id, promotion_id) VALUES (17, 11);

-- restart sequences above all inserted IDs to avoid collision on next API-created records
ALTER SEQUENCE Category_SEQ RESTART WITH 100;
ALTER SEQUENCE Product_SEQ RESTART WITH 100;
ALTER SEQUENCE Promotion_SEQ RESTART WITH 100;
