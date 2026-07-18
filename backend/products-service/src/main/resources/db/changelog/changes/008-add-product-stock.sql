-- liquibase formatted sql

-- changeset chakib:008-add-product-stock
ALTER TABLE product ADD COLUMN stock INTEGER;

-- backfill the dev-seed catalog (004) with varied, plausible inventory levels
UPDATE product SET stock = 8  WHERE id = 10;
UPDATE product SET stock = 15 WHERE id = 11;
UPDATE product SET stock = 40 WHERE id = 12;
UPDATE product SET stock = 5  WHERE id = 13;
UPDATE product SET stock = 3  WHERE id = 14;
UPDATE product SET stock = 22 WHERE id = 15;
UPDATE product SET stock = 11 WHERE id = 16;
UPDATE product SET stock = 6  WHERE id = 17;
UPDATE product SET stock = 30 WHERE id = 18;
UPDATE product SET stock = 9  WHERE id = 19;
UPDATE product SET stock = 50 WHERE id = 20;
UPDATE product SET stock = 14 WHERE id = 21;
