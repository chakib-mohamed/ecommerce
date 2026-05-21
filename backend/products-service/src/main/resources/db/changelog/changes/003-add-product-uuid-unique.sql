-- liquibase formatted sql

-- changeset chakib:003-add-product-uuid-unique
ALTER TABLE product ADD CONSTRAINT uq_product_uuid UNIQUE (uuid);
